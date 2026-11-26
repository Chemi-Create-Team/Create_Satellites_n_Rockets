# 外部MOD向け API（ペイロード・ロケット）

他のMODは2つのものを登録できます。

- **ペイロード**（CAE 0.1.6以降、重さは0.1.7以降）：自分のアイテムを人工衛星として登録します。プレイヤーがそれを発射台のロケットに積んで打ち上げると、軌道上にそのMODの衛星ができます。
- **ロケット**（CAE 0.1.7以降、`API_VERSION` 2）：自分のアイテムをロケットの種類として登録します。最大積載量・燃料・上昇・大きさを決め、見た目は自由に描けます。

CAE 0.1.8（`API_VERSION` 3）では、登録できるロケットの大きさを幅10・高さ60ブロックまで、0.1.10（`API_VERSION` 4）では幅16・高さ100ブロックまで拡張しました。

GPSや通信の中継のような機能は、登録したMODが衛星の位置とデータを読んで実装します。

依存は双方とも任意です。CAEは登録したMODを知らず、登録したMODもCAEがない環境で動きます。

## 組み込む

1. `dist/create-aerospace-engineering-api-0.1.15.jar` を `compileOnly` に追加します。ハッシュは `dist/SHA256SUMS` にあります。
2. `neoforge.mods.toml` で `create_aerospace_engineering` を `type = "optional"`、`versionRange = "[0.1.7,)"`（失敗の確率・`Hooks.failed`・`state` を使う場合は `[0.1.11,)`、幅16・高さ100までの寸法を使う場合は `[0.1.10,)`、幅10・高さ60までなら `[0.1.8,)`、重さやロケットを使わない場合は `[0.1.6,)`） として宣言します。
3. 実行時に `API_VERSION` を確かめるときは、**必要な版以上か**（`>=`）で比べます。`API_VERSION` は機能を足すたびに上がり、それまでの呼び出しは同じ意味のまま使えます。等号（`== 1` など）で比べると、新しいCAEで連携が止まります。

   | `API_VERSION` | CAE | 足したもの |
   | --- | --- | --- |
   | 1 | 0.1.6 | ペイロードAPI |
   | 2 | 0.1.7 | 重さ（`massKg`）、ロケットAPI |
   | 3 | 0.1.8 | ロケットの大きさ 幅10・高さ60まで |
   | 4 | 0.1.10 | ロケットの大きさ 幅16・高さ100まで |
   | 6 | 0.1.17 | `Recovery`（第1段の帰還）、`flights`・`needsService`・`withService`、`Context.returning`・`returnTicks`、`Renderer.returnExhaust` |
   | 5 | 0.1.11 | `failureChance`、`Hooks.failed`、`state`（0.1.15のJARも同じAPI。登録のロックを1つにまとめ、`visibleFrom` を速くした） |

4. CAEのクラスを参照するのは連携用の1クラスだけにし、`ModList.get().isLoaded("create_aerospace_engineering")` を確かめてから呼びます。CAEがない環境では、そのクラスを読み込みません。

```java
// FMLCommonSetupEvent.enqueueWork の中（サーバーとクライアントの両方。クライアントはペイロード名の表示に使う）
if (ModList.get().isLoaded("create_aerospace_engineering")) ExampleCaeCompat.register();
```

```java
import net.createaerospace.api.AerospaceApi;
import net.createaerospace.api.AerospaceApi.Payload;
import net.createaerospace.api.AerospaceApi.SatelliteInfo;

final class ExampleCaeCompat {
    static final ResourceLocation GPS = ResourceLocation.fromNamespaceAndPath("examplemod", "gps");

    static void register() {
        AerospaceApi.registerPayload(GPS, ExampleItems.GPS_SATELLITE.get(), new Payload() {
            @Override
            public Component name() {
                return Component.translatable("examplemod.payload.gps");
            }

            @Override
            public int massKg(ItemStack stack) {
                return 800; // 1個の重さ。ロケットの最大積載量に数える
            }

            @Override
            public CompoundTag deploy(ServerLevel level, ItemStack stack, SatelliteInfo satellite) {
                var data = new CompoundTag();
                data.putInt("Channel", stack.getOrDefault(ExampleComponents.CHANNEL, 0));
                return data;
            }

            @Override
            public void removed(MinecraftServer server, SatelliteInfo satellite) {
                ExampleNetworks.forget(satellite.id());
            }
        });
    }

    /** 自分のGPS衛星が地平線の上に4機以上あれば位置が取れる。 */
    static boolean fix(MinecraftServer server, BlockPos pos) {
        return AerospaceApi.visibleFrom(server, pos).stream().filter(s -> s.payload().equals(GPS)).count() >= 4;
    }
}
```

## 打ち上げまで

- 登録したアイテムは、CAEの「人工衛星」と同じように、発射台に立てたロケット（CAEのものでも他のMODのものでも）に積めます。燃料・レッドストーン・打ち上げの流れは同じです。
- 重さは `Payload.massKg(stack)`（省略すると `DEFAULT_MASS_KG` = 500 kg、1 kg未満は1 kg）。積んだ重さの合計がロケットの最大積載量を超える分は積めません。1機に複数積むと、それぞれが別の衛星になります。
- ロケットはアイテムそのもの（1個ずつ、すべてのデータコンポーネントを含む）を載せます。発射前に発射台を壊すか、スニークして降ろすと、そのアイテムが戻ります。
- 金床で付けた名前が衛星の名前になります。名前がなければ `SAT-xxxx` です。
- 軌道に着くと `Payload.deploy` が呼ばれます。返した `CompoundTag` が衛星のデータになります（最大 `MAX_DATA_BYTES` = 16 KiB。超えたら空のまま）。
- 衛星は静止軌道に入ります。高度・傾斜角・モードは、CAEの地上管制コンソールと共通制御コンピュータで持ち主が変えます。地上管制の画面には「積荷」として `Payload.name()` が出ます。

## API

| 呼び出し | 内容 |
| --- | --- |
| `registerPayload(id, item, payload)` | 共通セットアップで1回。`id` は自分の名前空間で最大128文字。同じIDや同じアイテム、CAE自身の衛星アイテムは例外になります |
| `satellites(server)` / `satellites(server, payloadId)` | 軌道上の衛星（打ち上げ順）。位置は呼んだ時点のゲーム時刻で計算します |
| `satellite(server, uuid)` | 1機。いなければ空 |
| `elevation(server, uuid, blockPos)` | そのブロックから見た仰角（度）。0未満は地平線の下 |
| `visibleFrom(server, blockPos)` | 地平線の上にいる衛星。仰角の高い順 |
| `setData(server, uuid, tag)` | 衛星のデータを置き換えます。保存はCAEのワールドデータで、クライアントには送りません |
| `payloadOf(stack)` / `payload(id)` / `payloadName(id)` | 登録の照会 |
| `massKg(stack)` | ペイロード1個の重さ（kg）。ペイロードでなければ0 |

`SatelliteInfo` には、ID・名前・持ち主（そのペイロードを積んだプレイヤー）・ペイロードの種類・データのコピー・高度（km）・傾斜角（度）・周期（tick）・モード（0待機・1ビーコン・2点滅）・真下の緯度経度とブロック座標が入ります。位置は毎tick変わるので、保持せずに必要なときに呼び直してください。

- すべてサーバースレッドから呼びます。
- `removed` は、衛星が再突入で燃え尽きたとき、またはコマンドで取り除かれる直前に呼ばれます。データはまだ読めます。
- `state(server, uuid)`（API 5）は `ACTIVE`（運用中）・`ABANDONED`（放棄されたデブリ）・`REENTERING`（再突入中、まもなく `removed`）を返します。GPSや中継のように衛星の働きを使う機能は、`ACTIVE` だけを数えてください。`satellites` と `visibleFrom` は3つとも返します。再突入中の高度と位置は落ちていく今の値です。再突入が始まるとモードは0（待機）になるので、`state` のないAPI 1〜4のMODも、モード0を外せば再突入中の衛星を数えません。
- 登録したMODを外したワールドでも衛星は残ります。表示上の積荷名が消え、`payload` のIDは残るので、MODを戻せば続きから使えます。
- `massKg` はクライアントでもツールチップのために呼ばれます。スタックだけから決め、ワールドを見ないでください。
- `deploy` と `removed` で例外が出ても、CAEは記録して続けます（衛星は失われません）。

座標の対応は1度＝1000ブロック、xが経度（東がプラス）、-zが緯度（北がマイナスz）です。地球はMinecraftの1日で1回転します。

## ロケットAPI

`net.createaerospace.api.RocketApi`（共通）と `net.createaerospace.api.client.RocketRenderers`（クライアント）を使います。

```java
// FMLCommonSetupEvent.enqueueWork の中（サーバーとクライアントの両方。クライアントは大きさとツールチップに使う）
static final ResourceLocation HEAVY = ResourceLocation.fromNamespaceAndPath("examplemod", "heavy_lifter");

RocketApi.registerRocket(HEAVY, ExampleItems.HEAVY_LIFTER.get(), new RocketApi.RocketType(
        Component.translatable("item.examplemod.heavy_lifter"),
        4000,   // 最大積載量 kg
        8000,   // 発射1回の燃料 mB（0〜8000）
        320,    // 上昇する高さ（16〜1024ブロック）
        0.02,   // 加速度 ブロック/tick²
        3.0,    // 最高速度 ブロック/tick（10まで）
        3f, 9f  // 幅（16まで）・高さ（100まで）ブロック
), new RocketApi.Hooks() {   // 省略可（null でもよい）
    @Override
    public void launched(ServerLevel level, Entity rocket, List<ItemStack> payloads) { /* 発射の瞬間 */ }

    @Override
    public void deployed(ServerLevel level, Entity rocket, List<AerospaceApi.SatelliteInfo> satellites) { /* 放出した衛星 */ }

    @Override
    public void failed(ServerLevel level, Entity rocket, List<ItemStack> payloads) { /* 打ち上げ失敗、積荷は失われる（API 5） */ }
});
```

```java
// FMLClientSetupEvent の中（クライアントだけ）
RocketRenderers.register(HEAVY, new RocketRenderers.Renderer() {
    @Override
    public void render(RocketRenderers.Context c) {
        // c.pose() はロケットの底面の中心。y が上、単位はブロック、高さは c.height()
        // モデル・テクスチャ・RenderType・アニメーションは自由。c.buffers() から好きなバッファを取る
        int light = c.ascending() ? 0xF000F0 : c.packedLight();
        ExampleModels.HEAVY_LIFTER.render(c.pose(), c.buffers(), light, c.rocketItem(), c.ascentTicks() + c.partialTick());
    }

    @Override
    public void exhaust(ClientLevel level, double x, double y, double z, int ascentTicks, RandomSource random) {
        RocketRenderers.defaultExhaust(level, x, y, z, random);   // CAEの炎と煙に足すなら
        level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y - 1, z, 0, -0.5, 0);
    }
});
```

- 登録したアイテムを発射台に使うと、そのロケットが立ちます。アイテムは1個消費し、データコンポーネントごと保存して、降ろすと戻します（`Context.rocketItem()` で描画にも使えます。色や名前を付けたロケットを描き分けられます）。
- ペイロードの重さの合計が `maxPayloadKg` を超える分は積めません。発射には1個以上のペイロードと `fuelMb` 以上の燃料が要ります。
- 大きさはエンティティの当たり判定です。ロケットは発射台の中心に立つので、幅が1を超えると隣のブロックの上の空間も空いている必要があります。
- 描画を登録しなかった種類は、CAEのロケットのモデルを登録された幅・高さに合わせて描きます。
- CAEのH3ロケット（`rocket/H3`、描画は `client/H3Renderer`）もこのAPIで登録しています。旋盤の輪郭（高さと半径の組）で機体を作り、分離や噴射を描く例として参照できます。
- `Context` には種類のID、アイテム、上昇中か、上昇のtick数、積載重量・最大積載量・積んだ数、幅・高さ、ゲーム時刻、partialTick、PoseStack、バッファ、明るさ、帰還中か・帰還のtick数（API 6）が入ります。
- `RocketType` の9番目の値 `failureChance`（0〜1、API 5）は打ち上げに失敗する確率です。8つの値のコンストラクタ（API 4以前）では `DEFAULT_FAILURE_CHANCE` = 3% になります。失敗はゲームルール `caeLaunchFailures` で止められます。
- `RocketType` の10番目の値 `recovery`（API 6）を `new RocketApi.Recovery(noReturnChance, landingFailureChance)` にすると、衛星を放出したあと第1段が発射台に帰ってくる再使用ロケットになります（null なら使い捨て）。確率の合計は1以下です。帰還は `Recovery.HOLD_TICKS`（100）の静止と旋回、`FALL_TICKS`（240）の降下で、高さは `Recovery.fall(t)` に従います。着陸するとアイテムの飛行回数（`RocketApi.flights`）が1増え、要整備（`needsService`）になります。整備ガントリーが外すまで積載も発射もできません。`withService(stack, flights, needsService)` で書き換えられます。
- 帰還中は `Context.returning()` が true、`returnTicks()` が放出からのtick数です。`render` で姿勢や着陸脚を描き、`Renderer.returnExhaust(level, x, y, z, returnTicks, random)`（省略可）で帰還中のパーティクルと音を出します。CAEのファルコン9（`rocket/Falcon9`、`client/Falcon9Renderer`）が例です。
- `Hooks` と `Renderer` の例外はCAEが記録して続けます（ロケットも衛星も失われません。描画の例外は種類ごとに1回だけ記録）。
- 同じID・同じアイテムの二重登録や、ペイロードとして登録済みのアイテムは例外になります。範囲外の数値は `RocketType` の作成時に例外になります。

| 呼び出し | 内容 |
| --- | --- |
| `registerRocket(id, item, type[, hooks])` | 共通セットアップで1回 |
| `rocketOf(stack)` | そのアイテムが立てるロケットの種類。なければ null |
| `rocket(id)` / `rocketOrStandard(id)` | 登録の照会。後者は不明なIDならCAEのロケット |
| `STANDARD` / `MAX_FUEL_MB` | CAEのロケットのID、発射台の容量（8000 mB） |
| `MAX_WIDTH` / `MAX_HEIGHT` | ロケットの幅・高さの上限（16・100ブロック） |

