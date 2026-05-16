# Hwixel

Android group project management app for university students.

## Local Setup

Place Firebase configuration at:

```text
app/google-services.json
```

Keep local secrets in:

```text
local.properties
```

Expected AI key entry (Jatevo GPT-5.5):

```properties
gpt.api.key=your_jatevo_api_key
```

The base URL (`https://lb.jatevo.ai/v1`) and model (`gpt-5.5`) are baked into `BuildConfig` from `app/build.gradle.kts`.

Do not commit `local.properties`, `google-services.json`, keystores, or `keystore.properties`.
