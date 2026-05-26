# MiruPlay

> Android TV + Windows デスクトップ向けアニメメディアマネージャー — ローカルスクレイピング、クラウド同期、マルチソース再生

[简体中文](./README.md) | [English](./README.en.md)

## 現在の状態

- Android TV 版は引き続き主入口で、Jetpack Compose TV、Media3、Hilt を使用します。
- Windows デスクトップ版には Compose Desktop シェル、Local/WebDAV/SMB ソース、Bangumi メタデータ、CloudDrive2/RSS 設定、WebUI、mpv 再生、任意の RIFE runtime 対応が入っています。
- Windows 版の完了条件は [Windows port roadmap](./docs/windows-port-roadmap.md) で管理しています。実 CloudDrive2/RSS live QA、対象 GPU での RIFE matrix、署名済み installer 証跡、より広いデバイス E2E QA は対応する外部環境での検証が必要です。

## 特徴

- **マルチソースメディア** — ローカルファイル、WebDAV、SMB など複数のメディアソースに対応
- **メタデータ取得** — Bangumi からアニメ情報・ポスター・エピソード一覧を自動取得
- **RSS クラウド同期** — RSS 購読による新エピソードの自動同期
- **リモートコントロール** — 内蔵 HTTP サーバー + gRPC API、ブラウザやサードパーティクライアントから操作可能
- **TV リモコン最適化** — Compose + Leanback UI、十字キー操作に完全対応
- **Windows デスクトップ** — Compose Desktop UI、mpv 再生、任意の RIFE backend、デスクトップ JSON store
- **再生進捗管理** — エピソードごとの再生位置を記録し、続きから再生可能

## 技術スタック

| カテゴリ | 技術 |
|----------|------|
| 言語 | Kotlin 2.0.0 |
| UI | Jetpack Compose TV + Compose Desktop |
| DI | Hilt (Android) |
| データベース | Room (Android) + JSON store (Windows desktop) |
| プレーヤー | Media3 (ExoPlayer, Android) + mpv (Windows) |
| ネットワーク | OkHttp + NanoHTTPD + gRPC + Protobuf |
| シリアライズ | Kotlinx Serialization |

## ビルド

**要件：**
- JDK 21 (Temurin)
- Android SDK 35

```bash
# デバッグビルド
./gradlew assembleDebug

# リリースビルド（署名が必要）
./gradlew assembleRelease -PVERSION_NAME=1.0.0 -PVERSION_CODE=100

# テスト実行
./gradlew test

# リント
./gradlew lint

# 軽量 Windows デスクトップ install（ローカル mpv runtime をコピーしない）
./gradlew :desktop-app:installDist -PbundleMpvRuntime=false

# Windows port の安全なローカル gate
powershell -ExecutionPolicy Bypass -File tools/verify-windows-port.ps1
```

## プロジェクト構成

```
MiruPlay/
├── app/              # アプリエントリポイント、ナビゲーション、DI
├── core/
│   ├── model/       # ドメインモデル、データクラス
│   └── common/      # 共通ユーティリティ、Result 型
├── data/            # Room データベース、DAO、リポジトリ
├── ui-tv/           # TV Compose UI（画面、コンポーネント、テーマ）
├── ui-design/       # Android TV / Windows 共通の palette と入力契約
├── desktop-app/     # Windows Compose Desktop 入口、設定、WebUI bridge
├── player-core/     # Media3 プレーヤー統合
├── player-mpv/      # Windows mpv command、IPC、runtime 検証
├── media-source-api/# クロスプラットフォームの media source 契約
├── media-source/    # Android media source 実装
├── media-source-desktop/ # Windows Local / WebDAV / SMB source
├── repository-api/  # クロスプラットフォーム repository / 表示 helper
├── repository-desktop/ # Windows JSON-backed repository
├── scanner/         # ローカルファイルスキャナー
├── scanner-desktop/ # Windows scanner
├── scraper/         # アニメメタデータ取得（Bangumi）
├── scraper-core/    # 共有 scraper 契約と Bangumi mapping
├── scraper-desktop/ # Windows Bangumi client
├── sync-engine/     # RSS 同期エンジン
├── sync-engine-shared/ # 共有 Cloud/RSS action と directory browsing
├── sync-engine-desktop/ # Windows CloudDrive2/RSS runner と scheduler
├── cloud-drive/     # クラウドドライブ連携
├── cloud-drive-api/ # 共有 CloudDrive 契約
├── cloud-drive-desktop/ # Windows CloudDrive2 gRPC client
├── metadata/        # NFO メタデータ読み書き
├── metadata-core/   # 共有 NFO parse/write
├── web-control/     # HTTP コントロールサーバー
├── web-control-core/# 共有 WebUI HTTP routing と DTO
├── runtime/mpv/     # ローカル mpv/RIFE payload placeholder。大容量 file は未 commit
└── gradle/          # 依存関係バージョンカタログ
```

## ドキュメント

- [Windows port roadmap](./docs/windows-port-roadmap.md) — Windows 版の完了状況、必要な証跡、残りの live/target-host QA
- [Windows port plan](./docs/windows-port-plan.md) — architecture split、desktop entry、mpv/RIFE packaging 方針
- [Windows port audit](./docs/windows-port-audit.md) — 既存証跡と risk audit
- [mpv runtime packaging](./docs/mpv-runtime-packaging.md) — Windows mpv/RIFE runtime 準備と release gate

## ライセンス

[GNU General Public License v3.0](./LICENSE)
