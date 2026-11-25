# Create: Aerospace Engineering 開発規約

CAE（旧名 Create: Satellites 'n' Rockets）は、[Sable](https://modrinth.com/mod/sable) のコントラプションで動くロケット・人工衛星・宇宙ステーションを追加するCreateのアドオンです。姉妹MODの [Create: Refinery 'n' Reactors](https://github.com/Chemi-Create-Team/chemicreate-sub)（RnR）とは隣り合わせで使う前提ですが、RnRは任意です。ユーザー指定を優先し、外れる場合はREADMEの該当節かコミットメッセージへ理由を残します。

## 正本と作業範囲

| 項目 | 正本・方針 |
| --- | --- |
| 現行仕様 | [README.md](README.md)（ブロックの挙動・燃料・軌道・既知の制限） |
| ID・版 | `gradle.properties` と `CreateSatellites.MOD_ID`。mod ID `create_satellites_n_rockets`、Java `net.createsatellites` |
| 表示名 | `mod_name`（`gradle.properties`）と `itemGroup.create_satellites_n_rockets`（`lang/*.json`）、READMEの題名。現行は「Create: Aerospace Engineering」 |
| 依存 | NeoForge 21.1.228+（Minecraft 1.21.1）、Create 6.0.10+、Sable 2.0.6+。RnRは任意。版の基準は `gradle.properties` |
| 登録 | `CreateSatellites`（`DeferredRegister`）。ブロック5種、ブロックエンティティ2種、クリエイティブタブ1つ |
| リソース | `assets/` と `data/` は手書き。生成スクリプトは無く、直接編集してよい |
| 検証 | ローカルのみ（CIなし）。[文書と検証](#文書と検証) |

### 名称の扱い

表示名だけを変え、mod ID・Javaパッケージ・クラス名・リソースの名前空間（`create_satellites_n_rockets`）は変えません。変えるとワールド・レシピ・ブロックID・ディメンション `create_satellites_n_rockets:orbit` が壊れるためです。ID移行はユーザーが明示したときだけ行い、旧IDの救済（`MissingMappings` 等）を同時に用意します。新しい文言を書くときは、表示名には新名称を使い、旧名称へ戻しません。

## 版の管理

ユーザーが版を指定した場合はその指定を使います。指定がない場合は、ゲーム内の機能・挙動・表示を変えるコミットごとに `gradle.properties` の `mod_version` のパッチ番号を1増やします（`0.1.0` の次は `0.1.1`）。文書だけの更新では据え置きます。公開済みのタグや履歴は書き換えず、強制プッシュしません。大きな仕様変更はユーザーへ確認します。

## 維持する設計

- 本格的な宇宙シミュレーションにしない。天体は絵で、軌道運動はしない。空に見えるのは地球・月・太陽だけ（`client/OrbitSky`）。
- 船はSableのコントラプション。発射管制ブロックは接続したブロックを最大4096個まで1つのサブレベルにし、地面・石・土・砂・雪・岩盤・水・溶岩と発射台は残す（`tags/block/stays_behind.json`）。残すものを増やすときはこのタグを直す。
- 軌道への移動は `Orbit.transfer` が唯一の経路。Y 512超で地球から軌道へ、軌道のY -200未満で地球のY 480へ戻す。再突入の到着高度は `ASCENT_Y` より低く保つ（往復の振動を防ぐ）。
  - サブレベルは移動前と同じプロット枠を使う（ブロックエンティティが絶対プロット座標で保存されるため）。枠が埋まっていれば移動せず、0.5秒ごとに再試行する。
  - 両ディメンションの建築高さを同じにする（セクションは添字で保存）。`dimension_type/orbit.json` はオーバーワールドの高さを写している。変えない。
  - 速度・姿勢・乗っているプレイヤーを引き継ぐ。読み込みに失敗したら元のディメンションへ戻し、**船を失わない**。この復旧経路を弱めない。
- ロケットエンジン: タンク8000 mB、毎tick 4 mB消費、推力1200 N（`rocket_fuel_strong` は2倍）。推力は噴射口の反対向き。サーバーtickで値を書き、Sableの物理tickで読む（`volatile thrust`）。物理スレッドからワールドやブロックエンティティを触らない。
- 点火は、レッドストーンか、発射管制がサブレベルのユーザーデータへ書く `csr_ignited`。発射管制が点けるのは噴射口が下向きのエンジンだけで、制動用（他の向き）はレッドストーンにだけ従う。
- 燃料は流体タグ `rocket_fuel` / `rocket_fuel_strong`。RnRの流体はデータ（タグ）で足し、RnRのクラスを直接参照しない（RnRは任意）。
- 衛星コアは軌道上にいる間だけレッドストーン信号15を出す（20 tickごとに判定）。ステーションコアは無重力でだけ働く。
- Mixinを使わず、Sable・Createの公開APIを使う。Sable内部へ踏み込む箇所には理由をコメントで残す。
- 単純化で上限を作る箇所には `ponytail:` コメントで上限と拡張方法を書く（例: `Orbit.exchange` のプロット衝突の再試行）。
- 空の描画は素のテッセレータ。シェーダーパックが空を置き換える場合の非対応は既知の制限としてREADMEに記載済み。

## 文書と検証

1. ブロック・アイテム・レシピ・数値（推力・容量・高度・上限数など）を変えたら、同じ作業でREADMEの該当節と、`lang/` の3言語（`en_us`・`ja_jp`・`zh_cn`）を更新する。未確定値を推測で書かない。
2. 実装・レシピ・READMEの値を照合する。文書だけの変更ならビルドは不要で、リンクとコードの未変更を確認する。
3. Java・データ・レシピを変えたら次を実行する。ゲームテストは `run/world` を毎回消す必要がある。

```bash
./gradlew build
./gradlew runGameTestServer
./gradlew runClient -PquickPlay=<run/saves内のワールド名>
```

4. 非自明な処理（軌道の移動、点火、推力）を変えるときは、`gametest/OrbitGameTests` にテストを足すか更新する。レシピと全ブロックの登録は同テストが検査する。
5. 見た目（空、モデル、テクスチャ）を変えたら実画面で確認する。全MODの互換性や未測定の効果を保証しない。
6. 新しいブロックを足すときは、`blockstates` / `models/block` / `models/item` / `textures/block` / `loot_table/blocks` / `recipe` / `lang` の7点と、`data/minecraft/tags/block/mineable/pickaxe.json` と `needs_stone_tool.json` への登録も同じ変更で揃える。
7. 操作・配置・外観を説明する実機スクリーンショットは、リポジトリへ置いてREADMEから相対パスで参照する。

## 保管と整理

- `build/`・`run/`・`logs/`・`.gradle/` はコミットしない（`.gitignore` 済み）。
- 作業で出た一時ファイル・ログ・複製は終わる前に消す。
