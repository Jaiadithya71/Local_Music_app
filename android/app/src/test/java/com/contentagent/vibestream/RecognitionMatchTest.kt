package com.contentagent.vibestream
import com.contentagent.vibestream.data.analysis.RecognitionMatch
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class RecognitionMatchTest {
 private fun parse(text:String)=RecognitionMatch.parse(JsonParser.parseString(text).asJsonObject)
 @Test fun ambiguousSyntheticResponseIsRejected() {
  assertNull(parse("""{"matches":[{"id":"464953849","offset":181.346,"timeskew":0.013113,"frequencyskew":0.023029},{"id":"566463403","offset":307.8,"timeskew":-0.0125,"frequencyskew":-0.0051}],"track":{"key":"464953849","title":"Nippon Manju"}}"""))
 }
 @Test fun strictSingleConsistentMatchCanBeConsidered() {
  assertEquals("Title",parse("""{"matches":[{"id":"1","offset":12.3,"timeskew":0.0001,"frequencyskew":0.0001}],"track":{"key":"1","title":"Title","subtitle":"Artist"}}""")!!.title)
 }
 @Test fun skewMismatchAndMissingResultsStayUnknown() {
  assertNull(parse("{}"));assertNull(parse("""{"matches":[]}"""))
  assertNull(parse("""{"matches":[{"id":"1","offset":1,"timeskew":0.03,"frequencyskew":0}],"track":{"key":"1","title":"Wrong"}}"""))
 }
}
