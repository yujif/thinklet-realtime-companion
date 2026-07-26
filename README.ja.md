# THINKLET Realtime Companion

[English](README.md) | 日本語

カメラ映像とマイク音声を OpenAI Realtime API につないで、日本語音声で相談できる THINKLET 用アプリです。

## 概要

両手がふさがっている作業の最中は、そばに聞ける人がいないと調べものも困難です。
THINKLET を装着してこのアプリを起動しておくことで、声で質問して、短い音声の助言を受け取れます。
マイク音声を Realtime API へ送りつつ、カメラのフレームも定期的に送信します。
目の前の状況を言葉で説明しなくても、それを踏まえて応答します。

このサンプルでは、汎用的なリアルタイム会話と、特定の用途にあわせた2つのユースケース(料理支援、英会話練習)を用意しています。

### 主な機能

- OpenAI Realtime API への WebSocket 接続
- マイク音声の送信
- CameraX によるカメラ映像の録画とフレームサンプリング
- OpenAI 音声応答の再生
- Android TextToSpeech による状態通知
- セッションの記録(イベントログ、API 使用量、タイムライン、録画、音声、送信フレーム)
- 保存済みセッションを確認する HTML レビューの生成スクリプト

## 動作環境

- Android minSdk 27
- Android compileSdk 37
- Android targetSdk 35
- Java 17
- Kotlin 2.4.10
- Android Gradle Plugin 9.3.1
- Gradle 9.6.1（wrapper）
- THINKLET LC01 での利用を想定

## セットアップ

### ビルドとインストール

Android Studio でこのリポジトリを開くか、コマンドラインから Gradle wrapper を使います。
Jetpack Compose、CameraX、OkHttp、kotlinx.coroutines などのライブラリに依存しており、THINKLET App SDK は使用していません。

```bash
./gradlew build
```

実機へインストールする場合:

```bash
./gradlew installDebug
```

### カメラ・マイク権限の付与

初回起動時（および再インストール時）は、カメラとマイクの権限付与が必要です。

```bash
adb shell pm grant com.yujif.thinklet.realtimecompanion android.permission.CAMERA
adb shell pm grant com.yujif.thinklet.realtimecompanion android.permission.RECORD_AUDIO
```

### アプリ起動用のキーコンフィグ(任意)

THINKLET Launcher の中央ボタン長押しにこのアプリを割り当てる設定です。

```bash
adb push device-config/key_config_realtimecompanion.json /sdcard/Android/data/ai.fd.thinklet.app.launcher/files/key_config.json
```

### API キーの設定

アプリ起動後、画面上で OpenAI API キーを入力してください。
THINKLET 実機では、`scrcpy` で画面を PC に表示すると、PC 側から API キーをコピー & ペーストできて便利です。

このアプリは実験用の BYOK(Bring Your Own Key)方式で、通常の OpenAI API キーをそのまま端末で使います。
一時トークンを発行するバックエンドは用意していません。
入力した API キーは、Android Keystore のエクスポート不可な AES-GCM 鍵で暗号化して端末内のアプリ専用領域に保存し、Android のクラウドバックアップと端末間転送の対象からも外しています。
リポジトリに書き込まれることはありませんが、保存したキーの保護の強さは端末側のセキュリティ機構に依存します。

- 実験には、使用量に上限を設定した専用キーを使ってください。
- 不要になったキーは、アプリ画面の「キーを削除」ボタンから削除してください。

Realtime API の利用には API コストがかかります。
セッションを長時間続ける場合や、カメラフレームの送信頻度を上げる場合は、事前に料金と送信データ量を確認してください。

### Josee TTS のインストール

THINKLET は初期状態で英語のTTS エンジンが利用できますが、このアプリでは状態通知に日本語を使っており、[Josee TTS](https://github.com/FairyDevicesRD/droid.josee.tts) などの日本語TTSのインストールが必要です。
リポジトリの手順に従って APK をビルドして `adb install` でインストールしてください。

TTS エンジン、音声モデル、辞書は、このリポジトリには同梱していません。

## 使い方

### アプリの起動

セットアップ時にキーコンフィグを設定済みの場合は、中央ボタン長押しでアプリが起動します。

### ユースケースの選択

音量上ボタンで次のユースケース、音量下ボタンで前のユースケースに移り、中央ボタンの短押しで選択中のユースケースを開始します。
起動時のデフォルトは汎用リアルタイム会話です。

料理支援や英会話練習を最初から有効にしたビルドを入れるには、Gradle プロパティ `realtimeCompanionUseCase` にユースケース ID を渡して debug ビルドを作ります。

```bash
./gradlew installDebug -PrealtimeCompanionUseCase=cooking_support
./gradlew installDebug -PrealtimeCompanionUseCase=english_conversation_learning
```

未指定のときや未知の値を渡したときは、`generic_realtime_conversation` として起動します。

### 終了

中央ボタンの短押しで、リアルタイム相談を停止できます。
停止中はユースケースの切り替えができます。

### 記録の抽出・分析

実機から記録(セッションデータ)を抽出し、レポートを作成する手順は、[skills/thinklet-realtime-session-review/SKILL.md](skills/thinklet-realtime-session-review/SKILL.md) にまとめています。

## セッションデータ

### 記録される内容

実行時の記録は、THINKLET 上のアプリ外部ファイル領域に保存されます。

```text
/sdcard/Android/data/com.yujif.thinklet.realtimecompanion/files/sessions/
```

主な保存内容:

- `metadata.json`
- `events.jsonl`
- `usage.jsonl`
- `timeline.jsonl`
- `frames/`
- `camera.mp4`
- `mic.pcm`
- `assistant.pcm`
- `cooking-memory.json`(`cooking_support` 時のみ)

音声は、アシスタントの発話を `assistant.pcm` に、マイク入力を `mic.pcm` に保存します。
`generic_realtime_conversation` や `english_conversation_learning` など料理支援以外のユースケースでは、調理メモリーをプロンプトに反映せず、`cooking-memory.json` も保存しません。

> [!WARNING]
> 1セッションあたりの保存容量には上限があります。
> `camera.mp4` は CameraX 側で単独 1GB を上限とし、それ以外(`events.jsonl`、`timeline.jsonl`、`usage.jsonl`、`mic.pcm`、`assistant.pcm`、`frames/`)は合計 500MB を上限とします。
> 開始時には合計上限の 1.5GB 以上の空き容量を要求し、実行中は録画を含む合計サイズを監視します。
> 上限に達したとき、カメラ録画に失敗したとき、またはいずれかのファイルへの書き込みに失敗したときは、セッションを自動的に安全停止します。

保存済みセッションを削除するには、`sessions/` 配下の該当セッションディレクトリごと削除してください。
現時点では、アプリ内に一括削除の UI はありません。

### レビュー用HTML生成

保存済みセッションを PC 側で確認するための HTML レビュー生成スクリプトを用意しています。

```bash
python3 scripts/build_session_review_html.py --sessions-root /path/to/device-sessions --output review.html
```

生成された `review.html` は、ブラウザで直接開けます(`file:///path/to/review.html`)。

料理支援や英会話練習のセッションを確認するときは、生成時にユースケースを指定して表示を合わせます。
未指定の場合は `generic_realtime_conversation` のレビューとして表示されます。
`english_conversation_learning` を指定すると、料理支援専用の `update_cooking_memory` 診断は表示されません。

```bash
python3 scripts/build_session_review_html.py \
  --sessions-root /path/to/device-sessions \
  --output review.html \
  --use-case-id cooking_support \
  --use-case-label "Cooking Support"
python3 scripts/build_session_review_html.py \
  --sessions-root /path/to/device-sessions \
  --output review.html \
  --use-case-id english_conversation_learning \
  --use-case-label "English Conversation Learning"
```

## 開発

### ディレクトリ構成

```text
app/              Android アプリ本体
device-config/    THINKLET Launcher 用キーコンフィグ
gradle/           Gradle wrapper とバージョンカタログ
scripts/          セッションレビュー用補助スクリプト
skills/           セッションレビュー手順
tests/            Python 補助スクリプトのテスト
```

主な Kotlin パッケージ:

- `audio/`：マイク入力とアシスタント音声の再生
- `camera/`：CameraX 録画、フレームサンプリング、画像送信の制御
- `core/`：セッションのパス管理、コスト見積もり、ボタン制御、ログのマスキング
- `memory/`：調理メモリーのモデルと保存
- `openai/`：Realtime API の WebSocket 接続とユースケース別プロンプト設定
- `session/`：セッション記録と時計
- `tts/`：日本語 TextToSpeech 通知

### テスト

Android/Kotlin のユニットテスト:

```bash
./gradlew test
```

セッションレビュー生成スクリプトの Python テスト:

```bash
python3 -m unittest discover -s tests
```

### コントリビュート

変更を提案する場合は `CONTRIBUTING.md` を確認してください。
公開 issue や pull request には、API キー、署名素材、実機セッションの生データ、個人情報を含めないでください。

### 脆弱性の報告

脆弱性を見つけた場合は `SECURITY.md` を確認してください。
公開 issue には、脆弱性の詳細、API キー、Bearer トークン、実機セッションの記録(録音、映像、ログ)を貼らないでください。

## ライセンス

このリポジトリは MIT License で公開しています。詳細は `LICENSE` を確認してください。

Josee TTS など、同梱していない外部依存を利用する場合は、各配布元のライセンス条件に従ってください。
THINKLET App SDK など Fairy Devices の配布物が必要な場合は、公式の配布元から取得してください。

THINKLET は Fairy Devices 株式会社の登録商標です。
本リポジトリは THINKLET 向けの研究開発用リファレンス実装であり、Fairy Devices 株式会社の公式アプリではありません。
