# Create: Aerospace Engineering 0.1.12

[開発用API JAR](create-aerospace-engineering-api-0.1.11.jar) · [SHA-256](SHA256SUMS) · [変更履歴](../CHANGELOG.md) · [現行仕様](../docs/README.md)

本体のJARはリポジトリに置いていません。`./gradlew build` で `build/libs/create-aerospace-engineering-<版>.jar` を作り、クライアントとサーバーの両方に同じ版を入れてください。このフォルダーには、外部MODが `compileOnly` で使う開発用API JARを置いています。

## 必須環境

| 対象 | 版 |
| --- | --- |
| Java | 21 |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.228以上 |
| Create | 6.0.10以上 |

## 任意連携

| 使用機能 | 対応 |
| --- | --- |
| ロケット燃料のナフサ・メタノール・低硫黄ディーゼル・水素 | Create: Refinery 'n' Reactors 0.1.0以上。流体タグ `create_aerospace_engineering:rocket_fuel` で読み、入っていなくても動きます。[燃料](../docs/launch.md#発射台launch_pad) |
| `#c:fuel`・`#c:diesel` の燃料 | そのタグに流体を登録したMOD |
| 共通制御コンピュータの「人工衛星」アプリ | Create: Frontier Science 0.3.26以上（キャンバスAPI v2）。それより古いCFSでは何も追加しません。[共通制御コンピュータ](../docs/control-computer.md) |
| CFSのGPS衛星・ME中継衛星 | Create: Frontier Science 0.3.28以上のペイロード。CFS 0.3.31はCAEの `API_VERSION` が1（CAE 0.1.6）のときだけ衛星サービスを有効にするため、CAE 0.1.7以降と組み合わせるとGPSとME中継が無効になります（CFS側の既知の問題。打ち上げと地上管制は使えます） |
| 他のMODの人工衛星・ロケット | [外部MOD向けAPI](../docs/api.md) |

## 開発用API JAR

APIを変えた版だけJARを置いています。`API_VERSION` は機能を足すたびに上がり、古い呼び出しはそのまま使えます。

| JAR | `API_VERSION` | 内容 |
| --- | --- | --- |
| [0.1.11](create-aerospace-engineering-api-0.1.11.jar) | 5 | 打ち上げの失敗、`Hooks.failed`、`state`（0.1.12も同じAPI） |
| [0.1.10](create-aerospace-engineering-api-0.1.10.jar) | 4 | ロケットの大きさ 幅16・高さ100まで |
| [0.1.8](create-aerospace-engineering-api-0.1.8.jar) | 3 | ロケットの大きさ 幅10・高さ60まで |
| [0.1.7](create-aerospace-engineering-api-0.1.7.jar) | 2 | 重さとロケットAPI |
| 0.1.6（コミット `cf07d78` の `dist/`） | 1 | ペイロードAPI |

## 既存ワールドの更新

- 旧ID `create_satellites_n_rockets` で保存したワールドのブロック・アイテム・衛星は読めません。
- 軌道上の衛星はそのまま残ります。0.1.12からは、傾斜角を変えたときに真下の地点を保ちます。[軌道と座標](../docs/orbit.md#軌道の段階)
- 0.1.6以前に立てたロケットは標準ロケットとして扱い、積んでいた1個をそのまま運びます。[ロケット（エンティティ）](../docs/launch.md#ロケットエンティティ-rocket)
