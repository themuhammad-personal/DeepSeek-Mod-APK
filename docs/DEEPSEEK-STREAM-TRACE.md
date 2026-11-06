# DeepSeek 2.7.4 response stream trace

This note records a static trace of the supplied apktool/JADX analysis bundle. The files themselves are not committed.

## Evidence chain

1. `jw3.g(wu1, Long)` builds the chat request. For `ly1` it selects `/api/v0/chat/completion`; for the resume request model `tf2` it selects `/api/v0/chat/resume_stream`.
2. `wa3.b` is the Ktor media type `text/event-stream`.
3. `yca` is the serializer for `com.deepseek.chat.domain.chat.model.completion.sse.SseMessageRoot`; its only field is `response`. `jia` deserializes the streaming response as the mutable `gy` message model through `ey`.
4. `doc.c0(c26)` creates an `ada` SseMessageRoot from the initial JSON object.
5. `doc.W(st3, gv1)` maps each parsed event/delta into an `xq1` path/operation/data envelope, then applies it through `l23.y(s13, xq1, gv1, t13)`.
6. `l23.y` dispatches the supported `SET` operation to `s13.l(key, value, context)` and `APPEND` to `s13.m(value, key)`.
7. The root mutable model `gy` routes the `fragments` path to its mutable fragment list `ey1`. `ey1.c(index)` selects a fragment by index.
8. The mutable ResponseFragment implementation is `v44`. Its `l(String, c26, gv1)` replaces the `content` field; its `m(c26, String)` appends the decoded content chunk to the accumulated content. The completed/static representation is `gfa`.

## Correct candidate hook points

- Prompt request: `jy1.e(Lw94;Ljava/lang/Object;)V`, prompt field `c`.
- Streaming response SET: `v44.l(Ljava/lang/String;Lc26;Lgv1;)V`, content branch after the `dea.j(Object)` state update.
- Streaming response APPEND: `v44.m(Lc26;Ljava/lang/String;)V`, content branch after the `dea.m(Object,Object)` state update and its boolean move-result.

For APPEND, the second value argument to `dea.m` is the newly assembled full content string, not merely the incoming delta. For SET, the decoded value argument to `dea.j` is the replacement content string. A response observer placed after these updates can therefore parse complete content while it streams.

## Important limits

This is static call-graph evidence from the supplied 2.7.4 decode. It does not prove runtime callback frequency, lifecycle ordering, or UI-thread behaviour on a device. The observer should remain opt-in and queue proposals only. No command, MCP call, filesystem operation, or network tool should be executed until there is a separately verified approval UI and registered executor.

Do not use these obfuscated method fingerprints against another DeepSeek build without re-inspection.
