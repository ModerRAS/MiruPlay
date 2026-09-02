# MiruPlay

> Android TV アニメメディアマネージャー — ローカルスクレイピング、クラウド同期、マルチソース再生

[简体中文](./README.md) | [English](./README.en.md)

## 現在の状態

Android TV 版が主入口で、Jetpack Compose TV、Media3、Hilt を使用。動画（アニメ）モードと音楽モードの両方をサポートしています。CI は nightly と安定版を継続的に公開（現在のバージョンラインは 2.10.x）。

## 特徴

- **マルチソースメディア** — ローカルファイル、WebDAV、SMB、および AnimeOrganizer の MLIP library.db ソースに対応
- **メタデータ取得** — Bangumi からアニメ情報・ポスター・エピソード一覧・エピソードコメントを自動取得、ポスターはローカルキャッシュ
- **スマートファイル名解析** — 内蔵 AniFileBERT モデル（Android ONNX Runtime）によるファイル名解析。スキャン・スクレイピング・クラウド自動整理で使用
- **RSS クラウド同期** — RSS 購読による新エピソードの自動同期。CloudDrive2 オフラインダウンロードと自動整理に対応
- **ミュージックモード** — アルバム/トラックライブラリ、再生キュー、CUE トラック分割と通し再生、SRC 3段階のサンプリングレートバイパス
- **マルチバックエンドプレーヤー** — Media3 (ExoPlayer) がメイン、オプションで内蔵 mpv / ijkplayer バックエンド。ASS 字幕（libass レンダリング）、外部字幕・外部音声トラック、優先字幕言語、再生位置の再開
- **オーディオ DSP** — REW イコライザー（チャンネル別 PEQ）、NEON FIR 32/64-bit フィルタリング、FFT 分析
- **リモートコントロール** — 内蔵 HTTP サーバー + WebUI + gRPC API。ブラウザやサードパーティクライアントから操作可能。TV 側設定と同期を維持
- **TV リモコン最適化** — Compose + Leanback UI、十字キー操作に完全対応

## 技術スタック

| カテゴリ | 技術 |
|----------|------|
| 言語 | Kotlin 2.0.0 |
| UI | Jetpack Compose TV |
| DI | Hilt (Android) |
| データベース | Room |
| プレーヤー | Media3 (ExoPlayer) + オプションの mpv / ijkplayer バックエンド |
| 字幕 | libass による ASS 字幕レンダリング |
| ネットワーク | OkHttp + NanoHTTPD + gRPC + Protobuf |
| シリアライズ | Kotlinx Serialization |
| ML | AniFileBERT（ONNX Runtime）によるファイル名解析 |
| ビルド | Gradle 8.10 + AGP 8.6.0、JDK 21、minSdk 28 / targetSdk 35 |

## ビルド

**要件：**
- JDK 21 (Temurin)
- Android SDK 35

```bash
# デバッグビルド
./gradlew assembleDebug

# ビルド番号付きデバッグビルド（バージョンは 2.10.<BUILD_NUMBER> と表示）
./gradlew assembleDebug -PBUILD_NUMBER=123

# リリースビルド（署名が必要）
./gradlew assembleRelease -PVERSION_NAME=2.10.0 -PVERSION_CODE=100

# テスト実行
./gradlew test

# リント
./gradlew lint
```

## プロジェクト構成

```
MiruPlay/
├── app/                   # アプリエントリポイント、ナビゲーション、DI
├── core/
│   ├── model/            # ドメインモデル、データクラス
│   └── common/           # 共通ユーティリティ、Result 型
├── data/                 # Room データベース、DAO、リポジトリ
├── ui-tv/                # TV Compose UI（画面、コンポーネント、テーマ）
├── ui-design/            # Android TV 共通の palette と入力契約
├── player-core/          # Media3 プレーヤー統合、ミュージックキュー、オーディオ DSP ランタイム
├── player-mpv-android/   # 内蔵 mpv 再生バックエンド
├── player-ijkplayer-android/ # ijkplayer 再生バックエンド
├── audio-dsp-core/       # オーディオ DSP ロジック（PEQ など）
├── audio-dsp-native/     # NEON FIR / FFT ネイティブ実装
├── media-source-api/     # media source 契約
├── media-source/         # Android media source 実装（ローカル、WebDAV、SMB、MLIP）
├── repository-api/       # repository / 表示 helper
├── scanner/              # ローカルファイルスキャナー（音声ディレクトリ分類、タグ読み取り含む）
├── scraper/              # アニメメタデータ取得（Bangumi）
├── scraper-core/         # 共有 scraper 契約と Bangumi mapping
├── sync-engine/          # RSS 同期エンジン
├── sync-engine-shared/   # 共有 Cloud/RSS action と directory browsing
├── cloud-drive/          # クラウドドライブ連携
├── cloud-drive-api/      # 共有 CloudDrive 契約
├── cloud-drive-core/     # 共有 CloudDrive ロジック
├── metadata/             # NFO メタデータ読み書き
├── metadata-core/        # 共有 NFO parse/write
├── translation/          # 翻訳ヘルパー
├── background-task/      # バックグラウンドタスク
├── web-control/          # HTTP コントロールサーバー + WebUI フロントエンド
├── web-control-core/     # 共有 WebUI HTTP routing と DTO
└── gradle/               # 依存関係バージョンカタログ
```

## ドキュメント

- [BERT ファイル名解析とスキャン/整理接入](./docs/anime-filename-parser.md) — AniFileBERT の訓練成果物、Android ONNX ランタイム、スキャン処理、フォルダ/ファイル名の責務、ADB 検証手順
- [AniFileBERT メンテナンス](./docs/anifilebert-maintenance.md) — MiruPlay / AniFileBERT / AnimeName 三リポジトリの関係と、データ更新・再訓練・公開フロー
- [CloudDrive2 RSS オフラインダウンロード](./docs/cloud-drive-rss-offline-download.md) — RSS 取得、オフラインダウンロード、自動整理フロー
- [Media Source Content Mode](./docs/media-source-content-mode.md) — メディアソースのコンテンツモード（動画/音楽/混在）
- [Metadata Search Aggregation Refactor](./docs/metadata-search-aggregation-refactor.md) — メタデータ検索集約リファクタリングの記録
- [Android TV プレイヤー操作契約](./docs/android-tv-player-controls.md) — 再生コントロールのユーザー承認済みインタラクション契約
- [Android TV ADB 動作テスト](./docs/android-tv-behavior-tests.md) — adb 駆動の TV インタラクション動作テストスイート

その他のエンジニアリングドキュメントは `docs/agents/`（エージェントワークフローチェックリスト）、`docs/verification/`（実機検証記録）、`docs/workflows/`（リリース検証フロー）にあります。

## ライセンス

[GNU General Public License v3.0](./LICENSE)