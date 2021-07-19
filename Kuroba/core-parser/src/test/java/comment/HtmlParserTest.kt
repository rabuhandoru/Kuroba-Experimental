package comment

import com.github.k1rakishou.core_parser.comment.HtmlParser
import junit.framework.Assert.assertEquals
import org.junit.Test

class HtmlParserTest {

  @Test
  fun html_parser_test_1() {
    val html = "Test<a href=\"#p333650561\" class=\"quotelink\">&gt;&gt;33365<wbr>0561</a><br><span class=\"quote\">&gt;what&#039;s the<wbr>best alternative</span><br>Reps";

    val htmlParser = HtmlParser()
    val nodes = htmlParser.parse(html).nodes

    val expected = """Test
<a, href=#p333650561, class=quotelink>
&gt;&gt;33365
<wbr>
0561
<br>
<span, class=quote>
&gt;what&#039;s the
<wbr>
best alternative
<br>
Reps
""".lines()

    val actual = htmlParser.debugConcatIntoString(nodes).lines()
    assertEquals(expected.size, actual.size)

    actual.forEachIndexed { index, actualLine ->
      val expectedLine = expected[index]
      assertEquals(expectedLine, actualLine)
    }
  }

  @Test
  fun html_parser_test_2() {
    val html = "<s><a class=\"linkify twitter\" rel=\"noreferrer noopener\" target=\"_blank\" href=\"https://twitter.com/denonbu_eng/status/1388107521022468102\">https://twitter.com/denonbu_eng/sta<wbr>tus/1388107521022468102</a><a class=\"embedder\" href=\"javascript:;\" data-key=\"Twitter\" data-uid=\"denonbu_eng/status/1388107521022468102\" data-options=\"undefined\" data-href=\"https://twitter.com/denonbu_eng/status/1388107521022468102\">(<span>un</span>embed)</a></s>";

    val htmlParser = HtmlParser()
    val nodes = htmlParser.parse(html).nodes

    val expected = """<s>
<a, class=linkify twitter, rel=noreferrer noopener, target=_blank, href=https://twitter.com/denonbu_eng/status/1388107521022468102>
https://twitter.com/denonbu_eng/sta
<wbr>
tus/1388107521022468102
<a, class=embedder, href=javascript:;, data-key=Twitter, data-uid=denonbu_eng/status/1388107521022468102, data-options=undefined, data-href=https://twitter.com/denonbu_eng/status/1388107521022468102>
(
<span>
un
embed)
""".lines()

    val actual = htmlParser.debugConcatIntoString(nodes).lines()
    assertEquals(expected.size, actual.size)

    actual.forEachIndexed { index, actualLine ->
      val expectedLine = expected[index]
      assertEquals(expectedLine, actualLine)
    }
  }

  @Test
  fun html_parser_test_3() {
    val html = "<a href=\"/a/res/7272693.html#7272700\" class=\"post-reply-link\" data-thread=\"7272693\" data-num=\"7272700\">>>7272700</a><br>Ах ты пидор!!!!1<br>Хуй я тебе что посоветую теперь."

    val htmlParser = HtmlParser()
    val nodes = htmlParser.parse(html).nodes

    val expected = """
<a, href=/a/res/7272693.html#7272700, class=post-reply-link, data-thread=7272693, data-num=7272700>
>>7272700
<br>
Ах ты пидор!!!!1
<br>
Хуй я тебе что посоветую теперь.

    """.trimIndent().lines()

    val actual = htmlParser.debugConcatIntoString(nodes).lines()
    assertEquals(expected.size, actual.size)

    actual.forEachIndexed { index, actualLine ->
      val expectedLine = expected[index]
      assertEquals(expectedLine, actualLine)
    }
  }

  @Test
  fun html_parser_test_equals_symbols_inside_tag() {
    val html = "<a href=\"//boards.4channel.org/g/catalog#s=fglt\" class=\"quotelink\">&gt;&gt;&gt;/g/fglt</a>";

    val htmlParser = HtmlParser()
    val nodes = htmlParser.parse(html).nodes
    val expected = "<a, href=//boards.4channel.org/g/catalog#s=fglt, class=quotelink>\n&gt;&gt;&gt;/g/fglt\n".lines()

    val actual = htmlParser.debugConcatIntoString(nodes).lines()
    assertEquals(expected.size, actual.size)

    actual.forEachIndexed { index, actualLine ->
      val expectedLine = expected[index]
      assertEquals(expectedLine, actualLine)
    }
  }

  @Test
  fun html_parser_test_space_symbols_inside_tag() {
    val html = "<a href=\"//boards.4channel.org/  g/catalog#s=fglt\" class=\"quotelink\">&gt;&gt;&gt;/g/fglt</a>";

    val htmlParser = HtmlParser()
    val nodes = htmlParser.parse(html).nodes
    val expected = "<a, href=//boards.4channel.org/  g/catalog#s=fglt, class=quotelink>\n&gt;&gt;&gt;/g/fglt\n".lines()

    val actual = htmlParser.debugConcatIntoString(nodes).lines()
    assertEquals(expected.size, actual.size)

    actual.forEachIndexed { index, actualLine ->
      val expectedLine = expected[index]
      assertEquals(expectedLine, actualLine)
    }
  }

}