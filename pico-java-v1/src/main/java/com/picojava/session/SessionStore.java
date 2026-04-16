package com.picojava.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.picojava.common.JsonUtils;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

public class SessionStore {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private final Path root;

    public SessionStore(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root);
    }

    public SessionState create(String workspaceRoot) {
        String id = "session-" + FORMATTER.format(Instant.now()) + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        return SessionState.create(id, workspaceRoot);
    }

    public void save(SessionState state) throws IOException {
        state.normalize();
        state.touch();
        Path file = pathFor(state.getId());
        writeJsonAtomic(file, state);
    }

    public SessionState load(String sessionId) throws SessionException {
        Path file = pathFor(sessionId);
        if (!Files.exists(file)) {
            throw new SessionNotFoundException("未找到 session：" + sessionId);
        }
        SessionState state;
        try {
            state = JsonUtils.MAPPER.readValue(file.toFile(), SessionState.class);
        } catch (JsonProcessingException e) {
            throw new SessionCorruptedException("session 文件不是有效 JSON：" + file, e);
        } catch (IOException e) {
            throw new SessionCorruptedException("读取 session 文件失败：" + file, e);
        }
        state.normalize();
        validate(state, sessionId, file);
        return state;
    }

    public String latest() throws IOException {
        try (var stream = Files.list(root)) {
            Optional<Path> latest = stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified()));
            return latest.map(path -> stripExt(path.getFileName().toString())).orElse(null);
        }
    }

    public Path root() {
        return root;
    }

    public Path pathFor(String sessionId) {
        return root.resolve(sessionId + ".json");
    }

    private void validate(SessionState state, String requestedSessionId, Path file) throws SessionException {
        if (state.getSchemaVersion() > SessionState.CURRENT_SCHEMA_VERSION) {
            throw new SessionVersionException(
                    "session schema 版本 " + state.getSchemaVersion() + " 高于当前支持版本 "
                            + SessionState.CURRENT_SCHEMA_VERSION + "：" + file
            );
        }
        if (state.getSchemaVersion() < 0) {
            throw new SessionCorruptedException("session schema 版本无效：" + file);
        }
        if (!SessionState.SESSION_TYPE.equals(state.getSessionType())) {
            throw new SessionVersionException("不支持的 session 类型 '" + state.getSessionType() + "'：" + file);
        }
        if (state.getId().isBlank()) {
            throw new SessionCorruptedException("session id 缺失：" + file);
        }
        if (requestedSessionId != null && !requestedSessionId.isBlank() && !requestedSessionId.equals(state.getId())) {
            throw new SessionCorruptedException(
                    "session id 不匹配。期望 '" + requestedSessionId + "'，实际为 '" + state.getId() + "'：" + file
            );
        }
        if (state.getWorkspaceRoot().isBlank()) {
            throw new SessionCorruptedException("session workspaceRoot 缺失：" + file);
        }
        SessionState.RuntimeConfig runtimeConfig = state.getRuntimeConfig();
        String approvalPolicy = runtimeConfig.getApprovalPolicy();
        if (!approvalPolicy.isBlank() && !approvalPolicy.equals("ask") && !approvalPolicy.equals("auto") && !approvalPolicy.equals("never")) {
            throw new SessionCorruptedException("session approvalPolicy 无效：" + approvalPolicy);
        }
    }

    private void writeJsonAtomic(Path path, Object payload) throws IOException {
        Files.createDirectories(path.getParent());
        Path tempFile = Files.createTempFile(path.getParent(), path.getFileName().toString() + ".", ".tmp");
        try {
            JsonUtils.MAPPER.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), payload);
            try {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private String stripExt(String name) {
        return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }
}
