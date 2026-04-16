package com.picojava.agent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class ResponseParserTest {
    @Test
    void parsesFinalAnswer() {
        ModelTurn turn = ResponseParser.parse("<final>done</final>");
        Assertions.assertTrue(turn.isFinalAnswer());
        Assertions.assertEquals("done", turn.finalAnswer().content());
    }

    @Test
    void parsesJsonToolCall() {
        ModelTurn turn = ResponseParser.parse("<tool>{\"name\":\"read_file\",\"args\":{\"path\":\"README.md\"}}</tool>");
        Assertions.assertTrue(turn.isToolCall());
        Assertions.assertEquals("read_file", turn.toolCall().name());
        Assertions.assertEquals(Map.of("path", "README.md"), turn.toolCall().args());
    }

    @Test
    void parsesXmlToolCall() {
        ModelTurn turn = ResponseParser.parse("<tool name=\"write_file\" path=\"hello.txt\"><content>hello</content></tool>");
        Assertions.assertTrue(turn.isToolCall());
        Assertions.assertEquals("write_file", turn.toolCall().name());
        Assertions.assertEquals("hello.txt", turn.toolCall().args().get("path"));
        Assertions.assertEquals("hello", turn.toolCall().args().get("content"));
    }

    @Test
    void returnsRetryForMalformedToolJson() {
        ModelTurn turn = ResponseParser.parse("<tool>{bad json}</tool>");
        Assertions.assertTrue(turn.isRetry());
        Assertions.assertEquals("malformed_tool_json", turn.retryReason());
    }

    @Test
    void returnsRetryWhenToolArgsAreNotAnObject() {
        ModelTurn turn = ResponseParser.parse("<tool>{\"name\":\"read_file\",\"args\":\"bad\"}</tool>");
        Assertions.assertTrue(turn.isRetry());
        Assertions.assertEquals("tool_args_not_object", turn.retryReason());
    }

    @Test
    void parsesDelegateXmlToolBodyIntoTask() {
        ModelTurn turn = ResponseParser.parse("<tool name=\"delegate\" max_steps=\"2\">inspect README.md</tool>");
        Assertions.assertTrue(turn.isToolCall());
        Assertions.assertEquals("delegate", turn.toolCall().name());
        Assertions.assertEquals("inspect README.md", turn.toolCall().args().get("task"));
        Assertions.assertEquals("2", turn.toolCall().args().get("max_steps"));
    }

    @Test
    void retriesOnEmptyFinalAnswer() {
        ModelTurn turn = ResponseParser.parse("<final>   </final>");
        Assertions.assertTrue(turn.isRetry());
        Assertions.assertEquals("empty_final", turn.retryReason());
    }

    @Test
    void fallsBackToPlainTextFinalAnswer() {
        ModelTurn turn = ResponseParser.parse("plain answer");
        Assertions.assertTrue(turn.isFinalAnswer());
        Assertions.assertEquals("plain answer", turn.finalAnswer().content());
    }
}
