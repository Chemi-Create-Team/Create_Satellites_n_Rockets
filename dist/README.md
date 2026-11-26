# Create: Aerospace Engineering 0.1.21

[配布JAR](create-aerospace-engineering-0.1.21.jar) · [開発用API JAR](create-aerospace-engineering-api-0.1.21.jar) · [SHA-256](SHA256SUMS) · [変更履歴](../CHANGELOG.md) · [現行仕様](../docs/README.md)

クライアントとサーバーの両方を同じ版に更新し、旧CAE JARをmodsから取り除いてください。開発用API JARは、外部MODが `compileOnly` で使うものです。

0.1.21では、別の天体へ向かうペイロードのためのAPI `Payload.escape` を足しました（`API_VERSION` 7。Frontier Scienceの惑星探査機が使います）。0.1.20では、Frontier Science 0.3.38以降のとき、共通制御コンピュータのアプリのマップで衛星の点をクリックして選べ、液晶ごとの表示状態が再起動後も残るようにしました。0.1.19では、衛星マップを作り直しました。地球は雲の流れる1面8×8マスの小さな立方体で、衛星を点、軌道を軌道の段階ごとの色の円で描き、百数十機の衛星でも見やすく軽いままです。マップ画面では衛星の点や一覧をクリックして選べます。0.1.18では、ファルコン9の打ち上げ失敗の確率をほかのロケットと同じ3%にしました。0.1.17では、衛星を放出したあと第1段が発射台に帰ってくるファルコン9と、それを整備する整備ガントリーを追加しました。外部MODも `RocketType.recovery` で再使用ロケットを作れます（`API_VERSION` 6）。0.1.16では大型衛星パラボラアンテナの描画を軽くしました。0.1.15では、1機のロケットに何人かで積んだペイロードがそれぞれ積んだ人の衛星になり、衛星写真は通信衛星の持ち主かOPだけが撮れるようになりました（ゲームルール `caeSatellitePhotos` で停止可）。[変更履歴](../CHANGELOG.md)

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
| 衛星中継をMEネットワークから使う | Applied Energistics 2（19系）のMEストレージバス。AE2がなくても、ホッパーなど標準のアイテム搬送で使えます。[衛星中継](../docs/satellite-relay.md) |
| 共通制御コンピュータの「人工衛星」アプリ | Create: Frontier Science 0.3.32以上（キャンバスAPI v2）。0.3.38以上（API v3）では、マップの点のクリック選択と、液晶ごとの表示状態の保存も使えます。それより古いCFSとは一緒に起動できません（大型衛星パラボラの登録IDが重なるため）。[共通制御コンピュータ](../docs/control-computer.md) |
| CFSのGPS衛星・ME中継衛星 | Create: Frontier Science 0.3.32以上。CFSのME中継衛星はCAEの大型アンテナでも中継に使えます。[移設](../docs/satellite-relay.md#移設) |
| 他のMODの人工衛星・ロケット | [外部MOD向けAPI](../docs/api.md) |

## 開発用API JAR

APIを変えた版だけJARを置いています。`API_VERSION` は機能を足すたびに上がり、古い呼び出しはそのまま使えます。比べるときは `>=` を使ってください。

| JAR | `API_VERSION` | 内容 |
| --- | --- | --- |
| [0.1.21](create-aerospace-engineering-api-0.1.21.jar) | 7 | `Payload.escape`（別の天体へ向かうペイロード） |
| [0.1.17](create-aerospace-engineering-api-0.1.17.jar) | 6 | 再使用ロケット（`Recovery`）、帰還の描画 |
| [0.1.15](create-aerospace-engineering-api-0.1.15.jar) | 5 | 0.1.11と同じAPI。登録のロックを1つにまとめ、`visibleFrom` を速くした |
| [0.1.11](create-aerospace-engineering-api-0.1.11.jar) | 5 | 打ち上げの失敗、`Hooks.failed`、`state` |
| [0.1.10](create-aerospace-engineering-api-0.1.10.jar) | 4 | ロケットの大きさ 幅16・高さ100まで |
| [0.1.8](create-aerospace-engineering-api-0.1.8.jar) | 3 | ロケットの大きさ 幅10・高さ60まで |
| [0.1.7](create-aerospace-engineering-api-0.1.7.jar) | 2 | 重さとロケットAPI |
| 0.1.6（コミット `cf07d78` の `dist/`） | 1 | ペイロードAPI |

## 既存ワールドの更新

- 旧ID `create_satellites_n_rockets` で保存したワールドのブロック・アイテム・衛星は読めません。
- Frontier Science 0.3.31以前で置いた大型衛星パラボラアンテナは、同じIDのままCAEのブロックとして読み込まれ、所有者と向きを保ちます。[移設](../docs/satellite-relay.md#移設)
- 0.1.14以前に立てて発射していないロケットの積荷は、ロケットを立てたプレイヤーの衛星になります。
- 軌道上の衛星はそのまま残ります。0.1.14からは、傾斜角を変えたときに真下の地点を保ちます。[軌道と座標](../docs/orbit.md#軌道の段階)
- 0.1.6以前に立てたロケットは標準ロケットとして扱い、積んでいた1個をそのまま運びます。[ロケット（エンティティ）](../docs/launch.md#ロケットエンティティ-rocket)

以前の配布JARは [0.1.14](create-aerospace-engineering-0.1.14.jar)・[0.1.13](create-aerospace-engineering-0.1.13.jar)・[0.1.12](create-aerospace-engineering-0.1.12.jar) です。
