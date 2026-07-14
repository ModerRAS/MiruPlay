# MiruPlay

> Android TV アニメメディアマネージャー — ローカルスクレイピング、クラウド同期、マルチソース再生

[简体中文](./README.md) | [English](./README.en.md)

## 現在の状態

- Android TV 版は引き続き主入口で、Jetpack Compose TV、Media3、Hilt を使用します。

## 特徴

- **マルチソースメディア** — ローカルファイル、WebDAV、SMB など複数のメディアソースに対応
- **メタデータ取得** — Bangumi からアニメ情報・ポスター・エピソード一覧を自動取得
- **RSS クラウド同期** — RSS 購読による新エピソードの自動同期
- **リモートコントロール** — 内蔵 HTTP サーバー + gRPC API、ブラウザやサードパーティクライアントから操作可能
- **TV リモコン最適化** — Compose + Leanback UI、十字キー操作に完全対応
- **再生進捗管理** — エピソードごとの再生位置を記録し、続きから再生可能

## 技術スタック

| カテゴリ | 技術 |
|----------|------|
| 言語 | Kotlin 2.0.0 |
| UI | Jetpack Compose TV |
| DI | Hilt (Android) |
| データベース | Room |
| プレーヤー | Media3 (ExoPlayer) |
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
├── ui-design/       # Android TV 共通の palette と入力契約
├── player-core/     # Media3 プレーヤー統合
├── media-source-api/# media source 契約
├── media-source/    # Android media source 実装
├── repository-api/  # repository / 表示 helper
├── scanner/         # ローカルファイルスキャナー
├── scraper/         # アニメメタデータ取得（Bangumi）
├── scraper-core/    # 共有 scraper 契約と Bangumi mapping
├── sync-engine/     # RSS 同期エンジン
├── sync-engine-shared/ # 共有 Cloud/RSS action と directory browsing
├── cloud-drive/     # クラウドドライブ連携
├── cloud-drive-api/ # 共有 CloudDrive 契約
├── metadata/        # NFO メタデータ読み書き
├── metadata-core/   # 共有 NFO parse/write
├── web-control/     # HTTP コントロールサーバー
├── web-control-core/# 共有 WebUI HTTP routing と DTO
└── gradle/          # 依存関係バージョンカタログ
```

## ドキュメント

## ライセンス

[GNU General Public License v3.0](./LICENSE)
