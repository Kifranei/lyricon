# 保留 OpenAI SDK 的类（防止 R8 混淆导致 Jackson 序列化失败）
-keep class com.openai.** { *; }
-keep class com.fasterxml.jackson.** { *; }
