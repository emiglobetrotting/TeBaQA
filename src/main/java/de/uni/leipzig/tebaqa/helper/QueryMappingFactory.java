package de.uni.leipzig.tebaqa.helper;

import com.google.common.collect.Lists;
import com.hp.hpl.jena.rdf.model.RDFNode;
import de.uni.leipzig.tebaqa.controller.SemanticAnalysisHelper;
import de.uni.leipzig.tebaqa.model.QueryTemplateMapping;
import edu.stanford.nlp.simple.Sentence;
import joptsimple.internal.Strings;
import org.aksw.qa.commons.datastructure.Entity;
import org.aksw.qa.commons.nlp.nerd.Spotlight;
import org.apache.jena.rdf.model.Resource;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static de.uni.leipzig.tebaqa.helper.Utilities.ARGUMENTS_BETWEEN_SPACES;
import static java.lang.String.join;
import static org.apache.commons.lang3.StringUtils.getLevenshteinDistance;

/**
 * Creates a Mapping between a part-of-speech tag sequence and a SPARQL query.
 * Algorithm:
 * <br>Input: Question, dependencySequencePos(mapping between the words of the question and their part-of-speech tag),
 * QueryPattern from <i>sparqlQuery</i></br>
 * <ol>
 * <li>Get Named Entities of the <i>question</i> from DBpedia Spotlight.</li>
 * <li>Replace every named entity from step 1 in the <i><QueryPattern</i> with its part-of-speech tag.
 * If there is no exactly same entity to replace in step go to step 3.</li>
 * <li>Find possible matches based on string similarities:
 * <ol>
 * <li type="1">Create a List with all possible neighbor co-occurrences from the words in <i>Question</i>. Calculate the
 * levenshtein distance between every neighbor co-occurrence permutation and the entity from Spotlight</li>
 * <li type="1">If the distance of the likeliest group of neighbor co-occurrences is lower than 0.5 and the ratio between
 * the 2 likeliest group of words is smaller than 0.7, replace the resource in the <i>QueryPattern</i> with the
 * part-of-speech tags of the word group</li>
 * </ol>
 * </li>
 * <li>For every resource in the <i>QueryPattern</i> which isn't detected in the steps above, search for a
 * similar(based on levenshtein distance, see step 3) string in the question.</li>
 * <p>
 * </ol>
 */
public class QueryMappingFactory {

    private int queryType = -1;

    private static Logger log = Logger.getLogger(QueryMappingFactory.class);
    private String queryPattern = "";
    private Map<String, List<String>> unresolvedEntities = new HashMap<>();
    private String question = "";
    private List<RDFNode> nodes = new ArrayList<>();

    /**
     * Default constructor. Tries to create a mapping between a word or group of words and a resource in it's SPARQL
     * query based on it's POS tags.
     *  @param dependencySequencePos A map which contains every relevant word as key and its part-of-speech tag as value.
     *                              Like this: "Airlines" -> "NNP"
     * @param sparqlQuery           The SPARQL query with a query pattern. The latter is used to replace resources with their
     * @param nodes                 A list with all RDF nodes from DBpedia's ontology.
     */
    public QueryMappingFactory(String question, Map<String, String> dependencySequencePos, String sparqlQuery, List<RDFNode> nodes) {
        this.nodes = nodes;
        this.queryType = SemanticAnalysisHelper.determineQueryType(question);

        this.question = question;
        String queryString = Utilities.resolveNamespaces(sparqlQuery);
        List<String> permutations = getNeighborCoOccurrencePermutations(question.split(" "));
        //final String[] tmpQueryPatternString = replaceOntologyResources(question, dependencySequencePos, queryString);
        String tmpQueryPatternString = queryString;

        Map<String, List<Entity>> spotlightEntities = extractSpotlightEntities(question);
        tmpQueryPatternString = replaceSpotlightEntities(dependencySequencePos, permutations, tmpQueryPatternString, spotlightEntities);

        //log.info("unresolved: " + unresolvedEntities);
        queryString = tmpQueryPatternString;

        Pattern pattern = Pattern.compile("<(.*?)>");
        Matcher matcher = pattern.matcher(queryString);

        //Step 4: If there is a resource which isn't detected by Spotlight, search for a similar string in the question.
        //Find every resource between <>
        while (matcher.find()) {
            String resource = matcher.group(1);
            if (!resource.startsWith("<^") && !resource.startsWith("^")) {
                String[] split = resource.split("/");
                String entity = split[split.length - 1];
                //TODO in this case: Give me all launch pads operated by NASA. -> <http://dbpedia.org/ontology/LaunchPad> isn't recognized, because of the direct string matching!
                if (dependencySequencePos.containsKey(entity)) {
                    queryString = queryString.replace(resource,
                            "^" + dependencySequencePos.get(entity) + "^");
                } else {
                    //Calculate levenshtein distance
                    TreeMap<Double, String> distances = getLevenshteinDistances(permutations, entity);
                    queryString = conditionallyReplaceResourceWithPOSTag(dependencySequencePos, tmpQueryPatternString,
                            resource, distances);
                }
            }
        }

        this.queryPattern = queryString
                .replaceAll("\n", " ")
                .replaceAll("\\s+", " ");
    }

    public QueryMappingFactory(String question, String sparqlQuery, List<RDFNode> nodes) {
        this.nodes = nodes;
        this.queryType = SemanticAnalysisHelper.determineQueryType(question);

        this.question = question;
        String queryString = Utilities.resolveNamespaces(sparqlQuery);

        // queryString.replaceAll("<(.*?)>", )
        int i = 0;
        String regex = "<(.+?)>";
        Pattern pattern = Pattern.compile(regex);
        Matcher m = pattern.matcher(queryString);
        while (m.find()) {
            String group = m.group();
            if (!group.contains("^")) {
                queryString = queryString.replaceFirst(group, "<^VAR_" + i + "^>");
                i++;
            }
        }
        this.queryPattern = queryString
                .replaceAll("\n", " ")
                .replaceAll("\\s+", " ");
    }


    private String replaceSpotlightEntities(Map<String, String> dependencySequencePos, List<String> permutations,
                                            String queryPattern, Map<String, List<Entity>> spotlightEntities) {
        final String[] tmpQueryPattern = new String[1];
        tmpQueryPattern[0] = queryPattern;
        if (spotlightEntities.size() > 0) {
            spotlightEntities.get("en").forEach((Entity entity) -> {
                        String label = entity.getLabel();
                        List<Resource> uris = entity.getUris();
                        String[] words = label.split("\\s");
                        //Replace every named entity with its part-of-speech tag
                        //e.g.: <http://dbpedia.org/resource/Computer_science> => computer science => NN0 NN1 => ^NN0_NN1^
                        for (Resource uri : uris) {
                            String newQueryTemplate;

                            List<String> wordPos = new ArrayList<>();
                            for (String word : words) {
                                wordPos.add(dependencySequencePos.get(word));
                            }
                            String wordPosReplacement = "^" + join("_", wordPos) + "^";
                            if (tmpQueryPattern[0].toLowerCase().contains("<" + uri.toString().toLowerCase() + ">")) {
                                newQueryTemplate = tmpQueryPattern[0].replace(uri.toString(), wordPosReplacement);
                            } else {
                                //get the most similar word
                                TreeMap<Double, String> distances = getLevenshteinDistances(permutations, uri.getLocalName());
                                newQueryTemplate = conditionallyReplaceResourceWithPOSTag(dependencySequencePos, tmpQueryPattern[0],
                                        uri.toString(), distances);
                            }
                            tmpQueryPattern[0] = newQueryTemplate;
                        }
                    }
            );
        }
        return tmpQueryPattern[0];
    }

    private Map<String, List<Entity>> extractSpotlightEntities(String question) {
        //get named entities (consisting of one or multiple words) from DBpedia's Spotlight
        Spotlight spotlight = Utilities.createCustomSpotlightInstance("http://model.dbpedia-spotlight.org/en/annotate");
        spotlight.setConfidence(0.4);
        spotlight.setSupport("20");
        return spotlight.getEntities(question);
    }

    private String conditionallyReplaceResourceWithPOSTag(Map<String, String> dependencySequencePos,
                                                          String stringWithResources, String uriToReplace,
                                                          TreeMap<Double, String> distances) {
        String newString = stringWithResources;

        //Check if the difference between the two shortest distances is big enough
        if (distances.size() > 1) {
            Object[] keys = distances.keySet().toArray();
            //The thresholds are based on testing and might be suboptimal.
            if ((double) keys[0] < 0.5 && (double) keys[0] / (double) keys[1] < 0.7) {
                List<String> posList = new ArrayList<>();
                String[] split = distances.firstEntry().getValue().split(" ");
                for (String aSplit : split) {
                    posList.add(dependencySequencePos.get(aSplit));
                }
                if (newString.contains("<" + uriToReplace + ">")) {
                    newString = newString.replace(uriToReplace, "^" + join("_", posList) + "^");
                }
            } else {
                unresolvedEntities.put(uriToReplace, new ArrayList<>(dependencySequencePos.keySet()));
            }
        }
        return newString;
    }

    @NotNull
    private TreeMap<Double, String> getLevenshteinDistances(List<String> permutations, String string) {
        TreeMap<Double, String> distances = new TreeMap<>();
        permutations.forEach((word) -> {
            int lfd = getLevenshteinDistance(string, word);
            double ratio = ((double) lfd) / (Math.max(string.length(), word.length()));
            distances.put(ratio, word);
        });
        return distances;
    }

    private List<String> getNeighborCoOccurrencePermutations(String[] s) {
        List<String> permutations = new ArrayList<>();
        for (int i = 0; i <= s.length; i++) {
            for (int y = 1; y <= s.length - i; y++) {
                permutations.add(join(" ", Arrays.asList(s).subList(i, i + y)));
            }
        }
        return permutations;
    }

    private List<List<Integer>> createDownwardCountingPermutations(int a, int b) {
        List<List<Integer>> permutations = new ArrayList<>();
        for (int i = a; i >= 0; i--) {
            for (int y = b; y >= 0; y--) {
                List<Integer> newPermutation = new ArrayList<>();
                newPermutation.add(i);
                newPermutation.add(y);
                permutations.add(newPermutation);
            }
        }
        permutations.sort((List a1, List a2) -> ((int) a2.get(0) + (int) a2.get(1)) - ((int) a1.get(0) + (int) a1.get(1)));
        return permutations;
    }

    /**
     * Creates a SPARQL Query Pattern like this: SELECT DISTINCT ?uri WHERE { ^NNP_0 ^VBZ_0 ?uri . }
     * Every entity which is recognized with the DBPedia Spotlight API is replaced by it's part-of-speech Tag.
     *
     * @return A string with part-of-speech tag placeholders.
     */
    public String getQueryPattern() {
        return queryPattern;
    }

    public List<String> generateQueries(Map<String, List<QueryTemplateMapping>> mappings, String graph) {
        Set<String> rdfResources = new HashSet<>();

        Map<String, List<Entity>> spotlightEntities = extractSpotlightEntities(question);
        if (spotlightEntities.size() > 0) {
            spotlightEntities.get("en").forEach(entity -> entity.getUris().forEach(resource -> rdfResources.add(resource.getURI())));
        }

        for (String word : question.split("\\W+")) {
            rdfResources.addAll(getResources(word));
        }
        log.info("Found resources: " + Strings.join(rdfResources, "; "));

        List<String> classes = new ArrayList<>();
        List<String> properties = new ArrayList<>();
        for (String resource : rdfResources) {
            String s = resource.substring(resource.lastIndexOf('/') + 1);
            if (isClass(s)) {
                classes.add(resource);
            } else if (isProperty(s)) {
                properties.add(resource);
            } else {
                log.error("First char of resource is no valid character: " + resource);
            }
        }


        int classCount = classes.size();
        int propertyCount = properties.size();
        //if there is no suitable mapping with exactly the amount of properties and classes, reduce both consecutively
        //and try again.
        //TODO Use eccentric method in case their are less RDF resources than slots
        List<String> suitableMappings = new ArrayList<>();
        List<List<Integer>> downwardCountingPermutations = createDownwardCountingPermutations(classCount, propertyCount);
        for (List<Integer> permutation : downwardCountingPermutations) {
            Integer classCount1 = permutation.get(0);
            Integer propertyCount1 = permutation.get(1);
            suitableMappings = getSuitableMappings(mappings, classCount1, propertyCount1, queryType, graph);
            if (!suitableMappings.isEmpty()) {
                break;
            }
        }

        return Lists.newArrayList(fillPatterns(rdfResources, suitableMappings));
    }

    public List<String> generateQueries(Map<String, List<QueryTemplateMapping>> mappings) {
        return generateQueries(mappings, null);
    }


    private Set<String> fillPatterns(Set<String> rdfResources, List<String> suitableMappings) {
        Set<String> sparqlQueries = new HashSet<>();
        List<Map<String, String>> replacements = new ArrayList<>();
        List<String> toReplaceProperties = new ArrayList<>();
        for (String pattern : suitableMappings) {
            List<String> triples = Utilities.extractTriples(pattern);
            for (String triple : triples) {
                int argumentCnt = 0;
                List<String> toReplaceClasses = new ArrayList<>();
                triple = triple.replace("{", "").replace("}", "");
                Matcher argumentMatcher = ARGUMENTS_BETWEEN_SPACES.matcher(triple);
                while (argumentMatcher.find()) {
                    String argument = argumentMatcher.group();
                    if (argument.startsWith("<^") && Utilities.isEven(argumentCnt)) {
                        toReplaceClasses.add(argument);
                    } else if (argument.startsWith("<^") && !Utilities.isEven(argumentCnt)) {
                        toReplaceProperties.add(argument);
                    }
                    argumentCnt++;
                }
                for (String placeholder : toReplaceClasses) {
                    for (String rdfResource : rdfResources) {
                        if (isClass(rdfResource)) {
                            Map<String, String> mapping = new HashMap<>();
                            mapping.put(placeholder, rdfResource);
                            replacements.add(mapping);
                        }
                    }
                }
                for (String placeholder : toReplaceProperties) {
                    for (String rdfResource : rdfResources) {
                        if (isProperty(rdfResource)) {
                            Map<String, String> mapping = new HashMap<>();
                            mapping.put(placeholder, rdfResource);
                            replacements.add(mapping);
                        }
                    }
                }
            }

            List<String> classResources = new ArrayList<>();
            List<String> propertyResources = new ArrayList<>();
            for (String resource : rdfResources) {
                if (isClass(resource)) {
                    classResources.add(resource);
                } else if (isProperty(resource)) {
                    propertyResources.add(resource);
                }
            }

            sparqlQueries.add(Utilities.fillPattern(pattern, classResources, propertyResources));
        }
        return sparqlQueries;
    }

    private List<String> getSuitableMappings(Map<String, List<QueryTemplateMapping>> mappings, int classCount, int propertyCount, int queryType, String graph) {
        List<QueryTemplateMapping> templatesForGraph;
        if (graph == null) {
            templatesForGraph = mappings.values().stream().flatMap(List::stream).collect(Collectors.toList());
        } else {
            templatesForGraph = mappings.get(graph);
        }
        List<String> result = new ArrayList<>();
        if (queryType == SPARQLUtilities.ASK_QUERY) {

            result = templatesForGraph.stream()
                    //.filter(map -> map.getNumberOfClasses() <= classCount && map.getNumberOfProperties() <= propertyCount)
                    .map(QueryTemplateMapping::getAskTemplates)
                    .flatMap(Collection::stream).collect(Collectors.toList());

            //templatesForGraph.forEach(queryTemplateMapping -> result.addAll(queryTemplateMapping.getAskTemplates()));
        } else if (queryType == SPARQLUtilities.SELECT_QUERY) {

            result = templatesForGraph.stream()
                    // .filter(map -> map.getNumberOfClasses() <= classCount && map.getNumberOfProperties() <= propertyCount)
                    .map(QueryTemplateMapping::getSelectTemplates)
                    .flatMap(Collection::stream).collect(Collectors.toList());

            //templatesForGraph.forEach(queryTemplateMapping -> result.addAll(queryTemplateMapping.getSelectTemplates()));
        } else {
            result.addAll(templatesForGraph.stream()
                    //.filter(map -> map.getNumberOfClasses() <= classCount && map.getNumberOfProperties() <= propertyCount)
                    .map(QueryTemplateMapping::getAskTemplates)
                    .flatMap(Collection::stream).collect(Collectors.toList()));
            result.addAll(templatesForGraph.stream()
                    //.filter(map -> map.getNumberOfClasses() <= classCount && map.getNumberOfProperties() <= propertyCount)
                    .map(QueryTemplateMapping::getSelectTemplates)
                    .flatMap(Collection::stream).collect(Collectors.toList()));

            // templatesForGraph.forEach(queryTemplateMapping -> result.addAll(queryTemplateMapping.getAskTemplates()));
            // templatesForGraph.forEach(queryTemplateMapping -> result.addAll(queryTemplateMapping.getSelectTemplates()));
        }
        return result;
    }

    private List<String> getResources(String word) {
        String lemma = new Sentence(word).lemma(0);
        return nodes.stream()
                .filter(rdfNode -> rdfNode.toString().equalsIgnoreCase(String.format("http://dbpedia.org/ontology/%s", lemma)))
                .map(RDFNode::toString)
                .collect(Collectors.toList());
    }

    public Map<String, List<String>> getUnresolvedEntities() {
        return unresolvedEntities;
    }

    private boolean isProperty(String rdfResource) {
        return Character.isLowerCase(rdfResource.substring(rdfResource.lastIndexOf('/') + 1).charAt(0));
    }

    private boolean isClass(String rdfResource) {
        return Character.isUpperCase(rdfResource.substring(rdfResource.lastIndexOf('/') + 1).charAt(0));
    }
}