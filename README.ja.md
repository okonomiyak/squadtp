*[English README](README.md)*

# Squad Teleport (squadtp)

Minecraft 1.20.1 / Forge 47.x 向けの分隊(パーティー)Mod。
最大4人(設定可変)の分隊を作り、メンバーの現在地や集合地点へテレポートできる。
JourneyMap (5.9.x〜5.10.x) が入っていればメンバー位置と集合地点がウェイポイント表示される(**JourneyMapなしでも本体機能はすべて動作**)。

## ライセンス

GNU General Public License v3.0 (GPL-3.0-only)。全文は [`LICENSE`](LICENSE) を参照。

    squadtp — a squad (party) teleport mod for Minecraft
    Copyright (C) 2026 squadtp contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

## コマンド

| コマンド | 説明 | 権限 |
|---|---|---|
| `/squad create` | 分隊を作成(作成者がリーダー) | - |
| `/squad invite <player>` | 招待(チャットの[参加する]/[拒否する]で応答可) | リーダー |
| `/squad accept` / `/squad deny` | 招待に応答 | 被招待者 |
| `/squad join <player>` | その人の分隊に参加申請 | 未所属者 |
| `/squad approve <name>` / `/squad reject <name>` | 参加申請に応答 | リーダー |
| `/squad leave` | 脱退(リーダー脱退時は最古参メンバーへ自動委譲) | - |
| `/squad kick <member>` | キック(オフラインメンバーも名前指定可) | リーダー |
| `/squad promote <member>` | リーダー委譲 | リーダー |
| `/squad disband` | 解散 | リーダー |
| `/squad info` | メンバー・オンライン状態・集合地点を表示 | - |
| `/squad tp <member>` | メンバーの現在地へテレポート | - |
| `/squad setrally` | 現在地を集合地点に設定 | リーダー |
| `/squad rally` | 集合地点へテレポート | - |
| `/squad admin` | 機能スイッチ・蘇生詠唱時間の現在値を表示 | OP (レベル2+) |
| `/squad admin enable\|disable <機能>` | 機能をサーバー全体でON/OFF | OP (レベル2+) |
| `/squad admin revivetime <秒数>` | 蘇生の詠唱時間を実行時に変更(再起動不要、ワールドに永続化) | OP (レベル2+) |
| `/squad admin revivetime reset` | 詠唱時間をconfigの既定値に戻す | OP (レベル2+) |

### 管理者用の機能スイッチ

`/squad admin disable <機能>` でサーバー全体・実行時に個別機能を無効化できる(ワールドデータに永続化、再起動後も維持)。
対象: `create`(分隊作成) / `invite`(招待) / `join`(参加申請) / `tp`(メンバーTP) / `rally`(集合地点) / `respawn`(リスポーン選択) / `positions`(位置共有=マップ表示) / `dummy`(テストダミー)。
検証はすべてサーバー側の実行箇所で行うため、GUI・チャットボタン経由でも迂回できない。`positions` を無効にするとクライアントの位置表示も即クリアされる。

蘇生の詠唱時間(既定5秒、`reviveCastSeconds`)は `/squad admin revivetime <1〜60>` で**サーバー再起動なしに実行時変更**でき、ワールドデータに永続化される(`/squad admin revivetime reset` でconfigの既定値に戻す)。

## GUI

**Kキー**(キー設定「Squad Teleport」カテゴリで変更可)で分隊画面を開ける。

- 未所属時: 分隊作成ボタン、届いている招待の表示(「〇〇から招待が届いています」+承認/拒否)、オンラインプレイヤーへの「参加申請」ボタン
- 所属時: メンバー一覧(リーダー★・座標・オンライン状態、1秒ごとに自動更新)、各メンバーへの [TP]、リーダーなら [キック]/[委譲]、集合地点の表示/[移動]/[ここに設定]、[脱退]/[解散]
- リーダーには「参加申請」一覧([承認]/[拒否])と「招待できるプレイヤー」一覧(タブリスト上のオンラインプレイヤー)+ [招待] ボタン

**加入の導線は2通り**: ①リーダーが招待→相手が承認、②参加申請→リーダーが承認。どちらもGUIとチャットボタンの両方から操作できる。

**分隊の切り替え(乗り換え)**: 招待・参加申請とも**既に別の分隊に所属していても行える**。承諾/承認された瞬間に旧分隊を自動離脱してから新分隊に加入する(検証(満員・チーム制限など)は切り替え前にすべて通過させてから実行するため、失敗時に無所属になることはない)。旧分隊にも「〇〇が脱退しました」「新リーダーは〇〇です」を通知。GUIの「分隊に参加申請する」欄は所属中でも常時表示される(自分の分隊のメンバーは除外)。

GUIの全操作は対応する `/squad` コマンドをクライアントから送信するだけなので、権限チェックは従来どおりサーバー側で完結する(GUI追加による新たな攻撃面はない)。

## テストダミーブロック

1人で分隊機能をテストするための「プレイヤー代わり」ブロック(クリエイティブタブ「機能的ブロック」内、テクスチャはカボチャ)。

- 設置すると固有の名前(例: `Dummy_a1b2`)を持つ
- **分隊リーダーが右クリック**すると自分の分隊に参加/離脱をトグル
- 参加中はオンラインメンバーとして扱われ、位置同期・JourneyMapウェイポイント・`/squad tp Dummy_xxxx`・GUIのTPボタンの動作を1人で確認できる
- ブロックを壊すと分隊から自動離脱。チャンクがアンロードされている間は「オフライン」扱い
- ダミーはリーダーになれない(`/squad promote` は拒否され、自動委譲の対象からも除外。リーダー脱退でダミーだけが残る場合は分隊解散)

## 蘇生(ダウン)システム

分隊メンバーが致死ダメージを受けると、死亡せず**ダウン状態**になる(体力1固定・大幅な移動速度低下・画面に「ダウン」表示)。

- タイムアウト(既定30秒)以内に蘇生されなければ通常の死亡処理へ。**Gキー**(変更可)または `/squad giveup` で待たずに諦めて即死亡できる
- ダウン中は**伏せポーズ(泳ぎ姿勢)+発光アウトライン**になり、周囲・壁越しからも一目でダウンと分かる。**できるのは匍匐移動・チャット・ギブアップのみ**(ジャンプ/攻撃/採掘/アイテム・ブロック使用/ドロップ/持ち替え/インベントリ等の画面/全テレポート不可)
- **TACZ(銃Mod)連携**(オプション、ソフト依存): TACZ導入時、ダウン中は銃を撃てない(`GunFireEvent`/`GunShootEvent`をキャンセル)。TACZ非導入でも本体機能に影響なし
- **SuperbWarfare(銃Mod)連携**(オプション、ソフト依存): ダウン中は弾丸(バニラ`Projectile`型・`superbwarfare`名前空間)の生成そのものをキャンセルし、実質的に発射を無効化。SuperbWarfareにはTACZのような「発射前にキャンセルできるイベント」が存在しないための代替実装(銃の動作・音・弾薬消費は起きるが、弾は発生せずダメージも出ない)。SuperbWarfare側のクラスへの直接依存はゼロ(バニラの標準クラスと名前空間の文字列判定のみ)
- **接近アラート**: ダウン中に分隊メンバーが `approachAlertRadius`(既定24ブロック)以内に入ると、アクションバーに「〇〇 が接近中 (Nm)」+通知音(1回のみ、離れて再接近すれば再通知)。ベル音は分隊GUI下部の「設定」欄でプレイヤーごとにON/OFF可能(メッセージ表示自体は常時)
- 分隊メンバーがダウン中のプレイヤーを**右クリック長押し**(既定5秒)で蘇生。画面中央に進行ゲージ表示。**4ブロック以上離れる/離すと詠唱中断**
- 蘇生完了で最大体力の一部(既定30%)回復+短時間無敵(既定3秒)
- **ソロ(分隊未所属)はダウンを経由せず即死亡**。/kill・奈落などの即死系ダメージはダウンを貫通
- ダウン中はテレポート不可。ダウン中のログアウトは即死亡扱い(戦闘ログ対策)
- `/squad admin disable revive` でサーバー全体で無効化可能

設定 (`revive` セクション): `downedTimeoutSeconds`(30) / `reviveCastSeconds`(5、`/squad admin revivetime`で実行時上書き可) / `reviveHealPercent`(30) / `reviveInvulnSeconds`(3) / `allowNonSquadRevive`(false=分隊メンバーのみ蘇生可) / `approachAlertEnabled`(true、サーバーconfig) / `approachAlertRadius`(24、サーバーconfig)

接近アラートの**ベル音のみ**はプレイヤー個人の好みとしてクライアント設定(`config/squadtp-client.toml` の `bellSoundEnabled`)にあり、GUIの[ON]/[OFF]ボタンでいつでも切り替え可能(サーバーやワールドに依存しない)。

## 設定 (`world/serverconfig/squadtp-server.toml`)

- `squad.maxSquadSize` (既定 4) / `squad.inviteExpirySeconds` (既定 120)
- `squad.requireSameTeam` (既定 true) — バニラのチーム(`/team`)使用時、**分隊リーダーと同じチームのプレイヤーしか分隊に入れない**(両者ともチーム無しはOK、片方だけチーム有りはNG)。招待・承認・参加申請・申請承認のすべてで検証
- `teleport.tpCooldownSeconds` (既定 60、0で無効)
- `teleport.tpCostMode` = `NONE` / `XP` / `ITEM` (既定 NONE)
  - `tpCostXpLevels` (既定 3) / `tpCostItem` (既定 ender_pearl) / `tpCostItemCount` (既定 1)
- `teleport.combatBlockSeconds` (既定 15、0で無効) — メンバーが被ダメージ後この秒数の間、そのメンバーへの `/squad tp`・リスポーン選択スポーンをブロック(戦闘タグ)
- `teleport.allowCrossDimensionTp` (既定 true)
- `teleport.rallyRespawnEnabled` (既定 false) — 有効時、死亡リスポーン後に自動で集合地点へ移動(下の選択画面より優先)
- `teleport.respawnChoiceEnabled` (既定 true) — 死亡リスポーン後に「スポーン地点を選択」画面を表示(集合地点/オンラインメンバーの近く/そのまま)
- `teleport.respawnChoiceWindowSeconds` (既定 60) — 選択の有効時間。サーバーがリスポーン直後だけ `/squad respawn` を許可する仕組みで、平常時のテレポートには悪用できない
- `teleport.spawnDangerRadius` (既定 4、0で無効) — スポーン先のこの半径内に**敵対Mobまたは別チームのプレイヤー**がいる場合、リスポーン選択のスポーンをブロック
- `sync.posUpdateIntervalTicks` (既定 20) — 位置同期間隔

## 設計メモ

- **サーバー権威**: 分隊データは `SquadManager` (SavedData) がオーバーワールドに永続化。全操作はコマンド経由(=サーバー側実行)なので、クライアント→サーバーの独自パケットは存在せず、なりすましの余地がない。
- **同期**: S2Cのみ2種 — `SquadSyncPacket`(構成変更時)と `SquadMemberPosPacket`(定期位置配信、同分隊のみ)。
- **JourneyMap連携**: `compat/JourneyMapCompat` が唯一の入口。`ModList.isLoaded("journeymap")` が真のときだけ `compat/journeymap/` 以下(API参照クラス)をクラスロードする。プラグイン本体 `SquadJmPlugin` は `@ClientPlugin` 注釈によりJourneyMap側が発見・生成する。
- 対象APIは **JourneyMap API v1.9**(JourneyMap 1.20.1-5.9.x〜5.10.3 が実装)。

## ビルド・実行

要件: JDK 21(Gradle実行用。`gradle.properties` の `org.gradle.java.home` で指定。コンパイルはtoolchainのJava 17)

```
gradlew build        # → build/libs/squadtp-<version>.jar (配布用)
gradlew runClient    # 開発用クライアント1 (ユーザー名 Dev1, run/)
gradlew runClient2   # 開発用クライアント2 (ユーザー名 Dev2, run2/)
gradlew runServer    # 開発用サーバー (run-server/, online-mode=false設定済み)
```

### 2プレイヤーテスト手順

1. ターミナル3つ(またはIDEの実行構成)で `runServer` → `runClient` → `runClient2` を起動
2. 両クライアントで「マルチプレイ」→ サーバー `localhost` を追加して接続
3. Dev1で `/squad create` → KキーのGUIから Dev2 を [招待] → Dev2側のGUI/チャットで承認
   (逆方向は Dev2 が未所属状態のGUIから [参加申請] → Dev1 が [承認])
4. 互いのTP・位置表示・リスポーン選択などを確認

JourneyMap連携の開発環境テスト: `build.gradle` の `modRuntimeOnly 'curse.maven:journeymap-32274:5789363'` によりJourneyMap 5.10.3が難読化解除(remap)された状態で `runClient` に自動で入る。
TACZ連携も同様に `modRuntimeOnly 'curse.maven:timeless-and-classics-zero-1028108:8141310'` で開発環境に自動導入される。
**注意: 配布用のJourneyMap/TACZ jarを `run/mods/` に直接置いてはいけない**(SRG難読化されたままのためMixinが失敗し起動不能になる)。連携を外したいときは `build.gradle` の該当行をコメントアウトする。

## 動作確認手順(手動)

1. `gradlew runClient` を2窓、または `runServer` + クライアント接続で2アカウント用意
2. A: `/squad create` → `/squad invite B` → B: チャットの[参加する]
3. `/squad info` で2人表示・リーダー表記を確認
4. 互いに離れて `/squad tp <相手>` → クールダウンメッセージ(2回目)も確認
5. A: `/squad setrally` → B: `/squad rally`
6. JourneyMap導入時: フルスクリーンマップにメンバー(色付き)とRally(金色)のウェイポイントが約1秒間隔で追従することを確認
7. サーバー再起動後も分隊が維持されること(SavedData)を確認
