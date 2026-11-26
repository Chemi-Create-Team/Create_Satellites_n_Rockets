# 人工衛星のペイロード API

CAE 0.1.6 以降では、他のMODが自分のアイテムを人工衛星のペイロードとして登録できます。プレイヤーがそのアイテムをCAEの発射台に使うと、CAEのロケットが立ち、打ち上げると軌道上にそのMODの衛星ができます。GPSや通信の中継のような機能は、登録したMODが衛星の位置とデータを読んで実装します。

依存は双方とも任意です。CAEは登録したMODを知らず、登録したMODもCAEがない環境で動きます。

## 組み込む

1. `dist/create-aerospace-engineering-api-0.1.6.jar` を `compileOnly` に追加します。ハッシュは `dist/SHA256SUMS` にあります。
2. `neoforge.mods.toml` で `create_aerospace_engineering` を `type = "optional"`、`versionRange = "[0.1.6,)"` として宣言します。
3. CAEのクラスを参照するのは連携用の1クラスだけにし、`ModList.get().isLoaded("create_aerospace_engineering")` を確かめてから呼びます。CAEがない環境では、そのクラスを読み込みません。

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

- 登録したアイテムは、CAEの「人工衛星」と同じように発射台へ使えます。燃料・レッドストーン・打ち上げの流れは同じです。
- ロケットはアイテムそのもの（1個、すべてのデータコンポーネントを含む）を載せます。発射前に発射台を壊すと、そのアイテムが落ちます。
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

`SatelliteInfo` には、ID・名前・持ち主・ペイロードの種類・データのコピー・高度（km）・傾斜角（度）・周期（tick）・モード（0待機・1ビーコン・2点滅）・真下の緯度経度とブロック座標が入ります。位置は毎tick変わるので、保持せずに必要なときに呼び直してください。

- すべてサーバースレッドから呼びます。
- `removed` は、衛星がコンソールの軌道離脱かコマンドで取り除かれる直前に呼ばれます。データはまだ読めます。
- 登録したMODを外したワールドでも衛星は残ります。表示上の積荷名が消え、`payload` のIDは残るので、MODを戻せば続きから使えます。
- `deploy` と `removed` で例外が出ても、CAEは記録して続けます（衛星は失われません）。

座標の対応は1度＝1000ブロック、xが経度（東がプラス）、-zが緯度（北がマイナスz）です。地球はMinecraftの1日で1回転します。
