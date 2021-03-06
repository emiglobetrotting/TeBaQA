package de.uni.leipzig.tebaqa.helper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WikiTextUtilitiesTest {

    @Test
    public void testStripWikipediaContent() {
        String expected = "Hamburg, officially Freie und Hansestadt Hamburg (Free and Hanseatic City of Hamburg), is the second largest city in Germany and the eighth largest city in the European Union. It is the second smallest German state by area. Its population is over 1.7 million people, and the Hamburg Metropolitan Region (including parts of the neighbouring Federal States of Lower Saxony and Schleswig-Holstein) has more than 5 million inhabitants. The city is situated on the river Elbe. The official name reflects its history as a member of the medieval Hanseatic League, as a free imperial city of the Holy Roman Empire, a city-state, and one of the 16 states of Germany. Before the 1871 Unification of Germany, it was a fully sovereign state. Prior to the constitutional changes in 1919, the civic republic was ruled by a class of hereditary grand burghers or Hanseaten. Hamburg is a transport hub, being the 2nd largest port in Europe, and is an affluent city in Europe. It has become a media and industrial centre, with plants and facilities belonging to Airbus, Blohm + Voss and Aurubis. The radio and television broadcaster Norddeutscher Rundfunk and publishers such as Gruner + Jahr and Spiegel-Verlag are pillars of the important media industry in Hamburg. Hamburg has been an important financial centre for centuries, and is the seat of the world's second oldest bank, Berenberg Bank. The city is a notable tourist destination for both domestic and overseas visitors; it ranked 16th in the world for livability in 2015. The ensemble Speicherstadt and Kontorhausviertel was declared a World Heritage Site by the UNESCO in July 2015";
        String actual = WikiTextUtilities.stripWikipediaContent("Hamburg (/ˈhæmbɜːrɡ/; German pronunciation: [ˈhambʊʁk] , local pronunciation [ˈhambʊɪ̯ç] ; Low German/Low Saxon: Hamborg - [ˈhambɔːx] ), officially Freie und Hansestadt Hamburg (Free and Hanseatic City of Hamburg), is the second largest city in Germany and the eighth largest city in the European Union. It is the second smallest German state by area. Its population is over 1.7 million people, and the Hamburg Metropolitan Region (including parts of the neighbouring Federal States of Lower Saxony and Schleswig-Holstein) has more than 5 million inhabitants. The city is situated on the river Elbe. The official name reflects its history as a member of the medieval Hanseatic League, as a free imperial city of the Holy Roman Empire, a city-state, and one of the 16 states of Germany. Before the 1871 Unification of Germany, it was a fully sovereign state. Prior to the constitutional changes in 1919, the civic republic was ruled by a class of hereditary grand burghers or Hanseaten. Hamburg is a transport hub, being the 2nd largest port in Europe, and is an affluent city in Europe. It has become a media and industrial centre, with plants and facilities belonging to Airbus, Blohm + Voss and Aurubis. The radio and television broadcaster Norddeutscher Rundfunk and publishers such as Gruner + Jahr and Spiegel-Verlag are pillars of the important media industry in Hamburg. Hamburg has been an important financial centre for centuries, and is the seat of the world's second oldest bank, Berenberg Bank. The city is a notable tourist destination for both domestic and overseas visitors; it ranked 16th in the world for livability in 2015. The ensemble Speicherstadt and Kontorhausviertel was declared a World Heritage Site by the UNESCO in July 2015");
        assertEquals(expected, actual);
    }

    @Test
    public void testStripWikipediaContentWithBracketsAtTheEnd() {
        String expected = "German literary critic, philosopher and social critic";
        String actual = WikiTextUtilities.stripWikipediaContent("German literary critic, philosopher and social critic (1892-1940)");
        assertEquals(expected, actual);
    }
}
