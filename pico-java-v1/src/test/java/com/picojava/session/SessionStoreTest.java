package com.picojava.session;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

class SessionStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsStableSessionState() throws Exception {
        SessionStore store = new SessionStore(tempDir);
        SessionState state = store.create("repo-root");
        state.getHistory().add(new MessageEntry("user", "hello"));
        state.setDistilledMemory("memory");
        state.setMemoryState(new LinkedHashMap<>(java.util.Map.of("scratch", "value")));
        state.updateRuntimeConfig("auto", 7, 900, Set.of("OPENAI_API_KEY"), "openai", "gpt-5.4");
        state.recordRun(SessionState.RunInfo.fromTask(
                "run-1",
                "task-1",
                Instant.parse("2026-04-13T00:00:00Z"),
                Instant.parse("2026-04-13T00:00:10Z"),
                "completed",
                "final_answer_returned",
                "done",
                ""
        ));

        store.save(state);
        SessionState loaded = store.load(state.getId());

        Assertions.assertEquals(SessionState.CURRENT_SCHEMA_VERSION, loaded.getSchemaVersion());
        Assertions.assertEquals(SessionState.SESSION_TYPE, loaded.getSessionType());
        Assertions.assertEquals("repo-root", loaded.getWorkspaceRoot());
        Assertions.assertEquals(1, loaded.getHistory().size());
        Assertions.assertEquals("hello", loaded.getHistory().get(0).getContent());
        Assertions.assertEquals("memory", loaded.getDistilledMemory());
        Assertions.assertEquals("value", loaded.getMemoryState().get("scratch"));
        Assertions.assertEquals("auto", loaded.getRuntimeConfig().getApprovalPolicy());
        Assertions.assertEquals(7, loaded.getRuntimeConfig().getMaxSteps());
        Assertions.assertEquals(900, loaded.getRuntimeConfig().getMaxNewTokens());
        Assertions.assertEquals(List.of("OPENAI_API_KEY"), loaded.getRuntimeConfig().getSecretEnvNames());
        Assertions.assertEquals("run-1", loaded.getLatestRunId());
        Assertions.assertEquals(1, loaded.getRecentRuns().size());
    }

    @Test
    void latestReturnsNewestSavedSession() throws Exception {
        SessionStore store = new SessionStore(tempDir);
        SessionState first = store.create("repo-root");
        store.save(first);
        Files.setLastModifiedTime(store.pathFor(first.getId()), FileTime.from(Instant.parse("2026-04-13T00:00:00Z")));

        SessionState second = store.create("repo-root");
        store.save(second);

        Assertions.assertEquals(second.getId(), store.latest());
    }

    @Test
    void latestReturnsNullWhenStoreIsEmpty() throws Exception {
        SessionStore store = new SessionStore(tempDir);
        Assertions.assertNull(store.latest());
    }

    @Test
    void loadMissingSessionThrowsNotFound() throws Exception {
        SessionStore store = new SessionStore(tempDir);
        Assertions.assertThrows(SessionNotFoundException.class, () -> store.load("missing"));
    }

    @Test
    void loadCorruptedSessionThrowsCorruptedException() throws Exception {
        SessionStore store = new SessionStore(tempDir);
        Files.writeString(store.pathFor("broken"), "{not json", StandardCharsets.UTF_8);
        Assertions.assertThrows(SessionCorruptedException.class, () -> store.load("broken"));
    }

    @Test
    void loadFutureVersionThrowsVersionException() throws Exception {
        SessionStore store = new SessionStore(tempDir);
        Files.writeString(
                store.pathFor("future"),
                """
                {
                  "schemaVersion": 99,
                  "sessionType": "pico-java-session",
                  "id": "future",
                  "workspaceRoot": "repo-root"
                }
                """,
                StandardCharsets.UTF_8
        );
        Assertions.assertThrows(SessionVersionException.class, () -> store.load("future"));
    }

    @Test
    void loadInvalidApprovalPolicyThrowsCorruptedException() throws Exception {
        SessionStore store = new SessionStore(tempDir);
        Files.writeString(
                store.pathFor("bad-approval"),
                """
                {
                  "schemaVersion": 1,
                  "sessionType": "pico-java-session",
                  "id": "bad-approval",
                  "workspaceRoot": "repo-root",
                  "runtimeConfig": {
                    "approvalPolicy": "maybe"
                  }
                }
                """,
                StandardCharsets.UTF_8
        );
        Assertions.assertThrows(SessionCorruptedException.class, () -> store.load("bad-approval"));
    }
}
