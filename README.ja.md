# MiruPlay

> Android TV アニメメディアマネージャー — ローカルスクレイピング、クラウド同期、マルチソース再生

[简体中文](./README.md) | [English](./README.en.md)

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
| UI | Jetpack Compose + TV Material |
| DI | Hilt |
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
├── player-core/     # Media3 プレーヤー統合
├── media-source/    # メディアソース抽象化（ローカル / WebDAV / SMB）
├── scanner/         # ローカルファイルスキャナー
├── scraper/         # アニメメタデータ取得（Bangumi）
├── sync-engine/     # RSS 同期エンジン
├── cloud-drive/     # クラウドドライブ連携
├── metadata/        # NFO メタデータ読み書き
├── web-control/     # HTTP コントロールサーバー
└── gradle/          # 依存関係バージョンカタログ
```

## ライセンス

[GNU General Public License v3.0](./LICENSE)
