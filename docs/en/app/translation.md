# Translation

Lyricon can display translated lyrics. Translations can come from lyric providers or be generated
through an OpenAI-compatible API.

## Translation Sources

| Source                | Description                                            |
|:----------------------|:-------------------------------------------------------|
| Lyric provider        | The provider directly supplies translated lyrics       |
| OpenAI-compatible API | Lyricon calls the configured model to translate lyrics |

If the provider does not supply translations and AI translation is not configured, translated lyrics
will not be shown.

## Display Options

| Option                | Description                                              |
|:----------------------|:---------------------------------------------------------|
| Translation only      | Shows only translated lyrics and hides the original text |
| Hide translation      | Does not show translated lyrics                          |
| Ignore Chinese lyrics | Skips AI translation when all lyrics are Chinese         |

## OpenAI-Compatible Translation

Configure OpenAI translation in **App Styles** under the **Text** tab.

| Option          | Description                                              |
|:----------------|:---------------------------------------------------------|
| Target language | Output language, such as English or Japanese             |
| API key         | Key used to access the OpenAI-compatible service         |
| Model           | Model used for translation                               |
| Base URL        | Base URL of the OpenAI-compatible API                    |
| Custom prompt   | Prompt sent to the model to control translation behavior |

The model option can load available models. If loading fails, check the API key, Base URL, and
network connection.

## Advanced Parameters

| Parameter         | Description                                                        |
|:------------------|:-------------------------------------------------------------------|
| Temperature       | Controls randomness; lower is more stable, higher is more creative |
| Top P             | Controls nucleus sampling range                                    |
| Max tokens        | Limits generated length; `0` uses the service default              |
| Presence penalty  | Adjusts the tendency to introduce new wording                      |
| Frequency penalty | Adjusts repetition suppression                                     |

If unsure, keep the defaults.

## Clear Database

**Clear database** removes locally cached translation data. Use it when:

- You changed the prompt and want lyrics translated again.
- Translation results are abnormal and should be regenerated.
- You no longer want to keep cached translations.

This action cannot be directly undone.

## Privacy And Cost

When AI translation is used, lyric text is sent to the OpenAI-compatible service you configured. The
service may charge usage fees and may log requests. Review the provider's privacy policy and billing
rules.

## Common Issues

| Issue                           | Action                                                                  |
|:--------------------------------|:------------------------------------------------------------------------|
| API key is not set              | Enter a valid API key                                                   |
| No available models             | Check Base URL and API key, or enter a model name manually              |
| Translation is not shown        | Check hide translation, translation-only mode, and ignore-Chinese rules |
| Translation quality is unstable | Lower Temperature and improve the custom prompt                         |
