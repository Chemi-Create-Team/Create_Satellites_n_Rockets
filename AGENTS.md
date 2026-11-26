# Create: Aerospace Engineering 開発規約

CAE（旧名 Create: Satellites 'n' Rockets）は、ロケットを発射台から打ち上げ、静止軌道の人工衛星を地上管制コンソールで見て操作し、夜空に流れる衛星を眺めるCreateのアドオンです。宇宙へ行く仕様（軌道ディメンション）とSableとの連携はありません。姉妹MODの [Create: Refinery 'n' Reactors](https://github.com/Chemi-Create-Team/chemicreate-sub)（RnR）の燃料を使えますが、RnRは任意です。ユーザー指定を優先し、外れる場合はREADMEの該当節かコミットメッセージへ理由を残します。

## 正本と作業範囲

| 項目 | 正本・方針 |
| --- | --- |
| 現行仕様 | [README.md](README.md)（英語）と [README.ja.md](README.ja.md)（日本語。内容を一致させる）。ブロックの挙動・燃料・軌道・既知の制限 |
| ID・版 | `gradle.properties` と `CreateAerospace.MOD_ID`。mod ID `create_aerospace_engineering`、Java `net.createaerospace` |
| 表示名 | `mod_name`（`gradle.properties`）と `itemGroup.create_aerospace_engineering`（`lang/*.json`）、READMEの題名。現行は「Create: Aerospace Engineering」 |
| 依存 | NeoForge 21.1.228+（Minecraft 1.21.1）、Create 6.0.10+。RnRは任意。版の基準は `gradle.properties` |
| 登録 | `CreateAerospace`（`DeferredRegister`）。ブロック2種（発射台・地上管制コンソール）、アイテム1種（人工衛星）、エンティティ1種（ロケット）、ブロックエンティティ2種、Display Link用の表示ソース1つ、クリエイティブタブ1つ |
| API | `docs/api.md`、`dist/`（外部MOD向けのAPI JARとハッシュ） |
| リソース | `assets/` と `data/` は手書き。生成スクリプトは無く、直接編集してよい |
| 検証 | ローカルのみ（CIなし）。[文書と検証](#文書と検証) |

### 名称の扱い

旧名（Satellites 'n' Rockets、`create_satellites_n_rockets`、`net.createsatellites`、`CreateSatellites`）は残さず、すべて新名へ移行済みです。新しい文言・コード・ID・コマンドには旧名を使いません。

| 項目 | 現在の名前 |
| --- | --- |
| 表示名 | Create: Aerospace Engineering（CAE） |
| mod ID・リソースの名前空間 | `create_aerospace_engineering` |
| Java | パッケージ `net.createaerospace`、主クラス `CreateAerospace` |
| Gradle | `rootProject.name` は `create-aerospace-engineering` |
| 衛星の保存データ | `create_aerospace_engineering_satellites`（`MOD_ID` から作る） |

mod IDを変えたため、旧IDで保存したワールドのブロック・アイテム・衛星データは読めません（旧IDの救済は用意していない）。以後のID変更はユーザーが明示したときだけ行います。

## 版の管理

ユーザーが版を指定した場合はその指定を使います。指定がない場合は、ゲーム内の機能・挙動・表示を変えるコミットごとに `gradle.properties` の `mod_version` のパッチ番号を1増やします（`0.1.0` の次は `0.1.1`）。文書だけの更新では据え置きます。公開済みのタグや履歴は書き換えず、強制プッシュしません。大きな仕様変更はユーザーへ確認します。

## 維持する設計

- 衛星の位置は保存しない。円軌道の要素（高度段・傾斜段・昇交点・位相・基準tick・モード）だけを `SatelliteData`（オーバーワールドのSavedData）に持ち、位置は `OrbitMath` がゲーム時間（`getGameTime`、昼夜に依存しない）から閉じた式で求める。サーバーもクライアントも同じ式を使う。
- 地球は24000 tickで1回転する。軌道周期はゲーム用の縮尺（低軌道2分、静止軌道24000 tick）で、ケプラーの式ではない。1度 = 1000ブロック、経度は東が+x、緯度は北が-z（`OrbitMath`）。
- 打ち上げた衛星は静止軌道（通信衛星）で、発射経度の赤道上に止まる。高度ダイヤルを下げ、傾斜ダイヤルを上げると空を横切る。高度・傾斜の変更は位置を保つ（`Satellite.retuned`）。
- ロケットは普通のエンティティ（`RocketEntity`）と3Dモデル（`RocketModel`）。発射台に立っている間は静止し、発射台のブロックが消えたら衛星アイテムを落とす。上昇は物理を使わず（`noPhysics`）、200ブロック上昇したら衛星を放出して消える。
- 発射台は流体タンク8000 mB、1回の発射で4000 mB。タグ `rocket_fuel` の流体だけ入る。RnRの流体はデータ（タグ）で足し、RnRのクラスを直接参照しない（RnRは任意）。発射はレッドストーンの立ち上がり（新しい信号）だけ。
- 地上管制コンソールはRnRの原子炉制御盤と同じ作り。ボタンは天面にあり、クリック位置を操作者の座標系（`GroundConsoleBlock.uv`）で当てる。座標は `ConsoleButton`、向きは `FACING`（操作者の側を向く）、ダイヤルは左半分で下げ右半分で上げる。見た目の動く部分は `GroundConsoleRenderer`、状態はブロックエンティティの更新タグで同期する。
- 衛星の変更は所有者かOP（`Satellite.controlledBy`）だけ。閲覧は誰でもできる。軌道離脱はカバー（`GUARD_TICKS`）を開けた後の2回目のクリック。
- 衛星の一覧は `SatellitesPayload` でログイン時と変更時に全プレイヤーへ送る。名前の長さは `Satellite.MAX_NAME` で切る（パケット上限を超えるとプレイヤーが切断されるため）。
- 衛星マップ画面（`SatelliteMapScreen`）は管制室風の3列（左に衛星一覧、中央に立体の地球、右に選択中の読み取り）。**地球は立方体でできた球（`client/globe/VoxelGlobe`、半径28個、陸地は海より1個高く、極は白）で、衛星は小さなボクセルの機体として軌道半径（縮尺は無視。静止軌道で1.75倍）の位置に描く**。世界は平面の経度緯度の地図だが、それを世界地図として描かない（ユーザー指定）。`VoxelGlobe` はMinecraftの型を使わず、四角形を `Sink` へ渡すだけにして、画面（`GuiGraphics`）とCFSのキャンバスと単体の確認プログラムで共用する。陸地は `textures/gui/land_mask.png`（Natural Earth 1:50m、パブリックドメイン、512×256の白黒）から起動後に1回だけ作る。画面はドラッグで地球を回す。右の読み取りは `SatelliteReadout` を使い、コンソール・CFSアプリと同じ値にする。琥珀・緑・青の配色は画面とCFSアプリでそろえ、モードの色は `SatelliteReadout.MODE_COLORS` に置く。
- 夜空は `RenderLevelStageEvent` の `AFTER_SKY` で素のテッセレータを使う（`client/NightSky`）。シェーダーパックが空を置き換える場合の非対応は既知の制限。
- Mixinを使わず、Createの公開APIを使う。単純化で上限を作る箇所には `ponytail:` コメントで上限と拡張方法を書く。
- 地上管制コンソールは、CFS（Create: Frontier Science）0.3.26以降があるときだけ、共通制御コンピュータの「追加アプリ」（キャンバスAPI v2）としても使える（`compat/cfs`、描画は `client/CfsCanvasRenderer`）。**任意の連携で、必須依存にしない。** `net.createfrontier.*` を参照してよいのは `CfsComputerApp`（サーバー）と `CfsCanvasRenderer`（クライアント）だけで、`CfsCompat` が `ModList` と `ComputerCanvas`・`ComputerCanvasRenderers` クラスの有無を確かめてから呼ぶ。`libs/` のAPI JAR（`compileOnly`）はCFSの `dist/` の公式のものをそのまま使い、版を上げるときはハッシュを `build.gradle` のコメントに書く。CFS本体は変更しない。
- コンピュータのアプリは「制御」と「マップ」（立体の地球）の2画面。マップには地球を45度ずつ回すボタンがあり、向きは `Rot` としてフレームに入れる。ボタンとタブの位置は `CfsLayout`（正規化座標）だけに書き、描画（クライアント）とクリックの判定（サーバー）が同じものを使う。フレームに入れるのは表示画面・選択衛星・離脱カバーの期限だけで、衛星と時刻はクライアントが自分の写し（`ClientSatellites`、`gameTime`）から描く（時刻で変わる値をフレームに入れると毎回同期される）。
- **コンソールの機能を変えたら、コンピュータのアプリも同じ変更で直す。** 表示は `SatelliteReadout`（コンソールの画面・マップ画面・アプリの共通）、操作は `SatelliteControl`（コンソールのブロックとアプリの共通）に書く。コンソールにボタンや表示項目を足したときは、`SatelliteControl.appButtons()` の対象か、アプリに出せない理由を決め、`CfsLayout`・`CfsCanvasRenderer`・`computer.*` の翻訳（3言語）・両方のREADMEを更新する（レイアウトの整合は `OrbitGameTests` が検査する）。
- **外部MODの衛星はペイロードAPI（`api/AerospaceApi`）で受ける。** 外部のロケットは受けず、CAEのロケットが登録アイテムを1個そのまま載せる（ユーザー了承済みの方針）。APIの型はMinecraftとJavaのものだけにし、CFSなど外部MODへの依存を持たない。外部MODは `dist/create-aerospace-engineering-api-<版>.jar` を `compileOnly` で使うので、API（`net.createaerospace.api`）を変えた版では `./gradlew apiJar` でJARを作り、`dist/SHA256SUMS` と `docs/api.md` を同じコミットで更新する。公開済みのメソッドを消したり意味を変えたりせず、足すときは `API_VERSION` を上げる。衛星の種類は `Satellite.payload`（IDの文字列、空は通常の衛星）、データは `SatelliteData` にサーバー専用で保存し、衛星と一緒に消す。外部MODのコールバックの例外で衛星を失わない。

## 文書と検証

1. ブロック・アイテム・レシピ・数値（推力・容量・高度・上限数など）を変えたら、同じ作業でREADMEの該当節（英語の `README.md` と日本語の `README.ja.md` の両方）と、`lang/` の3言語（`en_us`・`ja_jp`・`zh_cn`）を更新する。未確定値を推測で書かない。
2. 実装・レシピ・READMEの値を照合する。文書だけの変更ならビルドは不要で、リンクとコードの未変更を確認する。
3. Java・データ・レシピを変えたら次を実行する。ゲームテストは `run/world` を毎回消す必要がある。

```bash
./gradlew build
./gradlew runGameTestServer
./gradlew runClient -PquickPlay=<run/saves内のワールド名>
```

4. 非自明な処理（軌道の式、発射、コンソールの操作、保存と通信）を変えるときは、`gametest/OrbitGameTests`（軌道・保存・通信）と `gametest/RocketGameTests`（発射台・ロケット・コンソール）にテストを足すか更新する。全ブロックがツルハシで掘れることも検査している。
5. 見た目（空、モデル、テクスチャ、コンソール）を変えたら実画面で確認する。`runClient` で新しいワールドを作るとき、`-PquickPlay` は既存のワールド名だけ受け付ける。全MODの互換性や未測定の効果を保証しない。
6. 新しいブロックを足すときは、`blockstates` / `models/block` / `models/item` / `textures/block` / `loot_table/blocks` / `recipe` / `lang` の7点と、`data/minecraft/tags/block/mineable/pickaxe.json` と `needs_stone_tool.json` への登録も同じ変更で揃える。
7. 操作・配置・外観を説明する実機スクリーンショットは、リポジトリへ置いてREADMEから相対パスで参照する。

## 保管と整理

- `build/`・`run/`・`logs/`・`.gradle/` はコミットしない（`.gitignore` 済み）。
- 作業で出た一時ファイル・ログ・複製は終わる前に消す。
