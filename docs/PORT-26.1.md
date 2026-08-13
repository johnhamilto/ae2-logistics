# Port: Minecraft 26.1.2 / NeoForge 26.1.2.95 / AE2 26.1.10-beta

Branch `mc-26.1`, bootstrapped 2026-08-12. The toolchain works end to end - Gradle
configures, every dependency resolves, javac runs - and `compileJava` fails with
3098 errors, fully inventoried below. `main` remains the shipping 1.21.1 line;
this branch tracks the port and stays WIP until `make check` and `make test` pass.

AE2 and GuideME are still BETA on this line (NeoForge 26.1.2.x is stable), so
expect upstream churn; re-check versions before each porting session.

## Toolchain (done)

- JDK 25: Homebrew `openjdk@25` (installed); the Makefile finds it.
- Gradle 9.1.0 wrapper, ModDevGradle 2.0.143, `java.toolchain` 25.
- NeoForge 26.1.2.95, AE2 26.1.10-beta, GuideME 26.1.12-beta - all on Maven
  Central. Version ranges updated in gradle.properties.
- Dev-runtime QoL (JEI, Jade) and the compat suite are OFF this branch until
  their 26.1 ports exist. The AppMek chemical bridge (compat/AppMekReturns,
  gametest/AppMekCompatHooks, two CompatGameTests methods, the AE2Logistics
  wiring) lives on main only and returns with the suite.
- `-Xmaxerrs 10000` in build.gradle while porting.
- Read-only reference matching our exact AE2 dep: `~/Projects/ae2-reference-26.1`
  (tag v26.1.10-beta). The 1.21.1 clone stays untouched at `~/Projects/ae2-reference`.

## Error inventory (3098 from `compileJava`, 2026-08-12)

| Cluster | ~Errors | Nature |
|---|---|---|
| ResourceLocation renamed Identifier (class/var/args/buf methods) | ~1000 | Mechanical rename |
| Gametest framework replaced (annotations -> registry instances) | ~700 | Redesign, pattern exists (see below) |
| AE2 part model API moved client-side (IPartModel/PartModels gone) | ~330 | Rework per part family |
| BE/part serialization: ValueInput/ValueOutput + Optional CompoundTag getters | ~400 | Semi-mechanical, AE2 patterns |
| GUI: GuiGraphics gone, screens/mouse events, appeng.init.client gone, Icon moved | ~200 | Rework per screen |
| NeoForge/vanilla renames (capabilities, network, InteractionResult, isClientSide) | ~150 | Mechanical |
| JEI integration (dep dropped on branch) | 46 | Restore dep if 26.1 JEI exists, else stub |
| Remainder (misc signatures) | ~250 | Case by case |

## Verified migration map

Everything here was read from the AE2 v26.1.10-beta clone or the NeoForge
26.1.2.95 sources jar - not guessed. Items marked TBD are unresolved.

- `ResourceLocation` -> `net.minecraft.resources.Identifier`. Factories keep
  their names: `Identifier.fromNamespaceAndPath(ns, path)` (AppEng.java:47),
  `Identifier.parse`. Expect `ResourceLocationArgument` -> Identifier variant and
  `FriendlyByteBuf.readResourceLocation` -> read-Identifier rename (TBD exact names).
- `Level.isClientSide` field is private: call `level.isClientSide()`
  (Platform.java:210).
- BE serialization: `loadAdditional(ValueInput)` / `saveAdditional(ValueOutput)`
  (AEBaseBlockEntity.java:143,184). Part API: `readFromNBT(ValueInput)` /
  `writeToNBT(ValueOutput)` (AEBasePart.java:196,205). CompoundTag getters return
  Optional now (`getInt` etc.); `getList` lost its type arg; AE2 uses the
  `getXxxOr(key, fallback)` forms. Our `saveToNBT/loadFromNBT(CompoundTag)`
  call sites follow the same ValueIO shape.
- `BlockEntityType.Builder.of(...).build(null)` ->
  `new BlockEntityType<>(supplier, blocks)` (AEBlockEntities.java:232).
- Capabilities renamed and moved onto the transfer/resource API:
  `Capabilities.ItemHandler.BLOCK` -> `Capabilities.Item.BLOCK`,
  `Capabilities.EnergyStorage` -> `Capabilities.Energy`, fluids likewise, with
  `ItemResource`/resource handlers instead of IItemHandler stacks
  (ItemP2PTunnelPart.java:11, FEP2PTunnelPart.java:32). This is a real rework
  for the mesh endpoint transport handlers, not a rename.
- Network: client->server sends go through `ClientPacketDistributor.sendToServer`
  (MEStorageScreen.java:337), which lives client-side.
- `FMLEnvironment.production` -> `FMLEnvironment.isProduction()` (NeoForge
  GameTestHooks.java). DevOnlyCondition and the creative-tab gate need the swap
  when this branch catches up with main's 0.34.0 dev-gating.
- `InteractionResult.sidedSuccess` gone, `ItemInteractionResult` gone - the
  1.21.2+ merged InteractionResult model. Map per call site.
- AE2 `Icon` enum: `appeng.client.gui.Icon` -> `appeng.util.Icon` (same
  SLOT_BACKGROUND member, Icon.java:181).
- `appeng.init.client.InitScreens` -> `appeng.client.InitScreens`, and ALL of
  AE2's client classes moved to a separate `src/client/java` source set (client
  jar split). Decide whether we adopt the same split (see decisions).
- Part models: `IPartModel`/`PartModels`/`PartModelsHelper`/`@PartModels` are
  gone from `appeng.api.parts`. Client-side model classes + a loader replace
  them (`appeng.client.model.PartModels`, per-part model classes like
  `PlanePartModel`, `PartModelLoaderMixin`). Port each part family against the
  closest AE2 part model class; expect our `part(...)` registration helper to
  lose its `PartModels.registerModels` call entirely.
- `Player.displayClientMessage(msg, false)` -> `sendSystemMessage(msg)`;
  `displayClientMessage(msg, true)` -> `sendOverlayMessage(msg)` (Player.java:1399,1402).
- `ResourceKey.location()` -> `identifier()` (ResourceKey.java:55).
- `net.minecraft.world.inventory.ClickType` -> `...inventory.ContainerInput`
  (AEBaseMenu.clicked signature).
- `IdentifierArgument` exists (commands/arguments) - the blind rename was right.
- Registry lookups: `BuiltInRegistries.X.get(id)` returns `Optional<Reference<T>>`
  now; the direct-value form is `getValue(id)` (SwitchGuisPacket.java:35).
- `GameTestAssertException(Component message, int tick)` - String ctor gone.
  `GameTestHelper.assertTrue/assertFalse(boolean, String)` still exist.
- Gametests: annotations (`@GameTest`, NeoForge `@GameTestHolder`,
  `@PrefixGameTestTemplate`) no longer exist. Vanilla now registers
  `GameTestInstance`s (with `TestData`, environments) in registries; NeoForge
  fires `RegisterGameTestsEvent` (mod bus) exposing
  `registerTest(Identifier, GameTestInstance)` + `registerEnvironment(...)`,
  gated by `GameTestHooks.isGametestEnabled()`. AE2's pattern: a custom test
  instance type (`GameTestPlotAdapter` + codec registered under
  `Registries.TEST_INSTANCE_TYPE` in AppEngBase:172-175) that wraps their plots
  and runs a `GameTestHelper` consumer. Ours: one adapter class holding a static
  registry of (name, template, timeoutTicks, Consumer<GameTestHelper>) entries,
  registered from RegisterGameTestsEvent; each of the 23 *GameTests classes
  becomes a list of entries with the method bodies unchanged. Also:
  `GameTestHelper.getBlockEntity(pos)` -> `getBlockEntity(pos, SomeType.class)`
  (216 call sites, PlotTestHelper.java:70) and the `GameTestAssertException`
  constructor changed (TBD: new signature - AE2 throws via helper methods).
- GUI: `GuiGraphics` no longer resolves (106 sites); AE2's AEBaseScreen and
  widgets live in the client source set with the new render-state model, and
  `mouseClicked` signatures changed (TBD exact shapes - read
  `src/client/java/appeng/client/gui/AEBaseScreen.java` when porting screens).
- `Player.displayClientMessage`, `hasPermission(int)`, `ClickType`,
  `Block.onRemove` signature, `GameRenderer` shader getters
  (EndpointHighlighter): all changed, exact replacements TBD per site.

## Port order

1. Mechanical sweep: Identifier rename, `isClientSide()`, Icon import,
   capability constant renames, `FMLEnvironment.isProduction()`,
   `new BlockEntityType<>`. Should clear roughly a third of the errors with
   near-zero design risk.
2. Serialization: ValueInput/ValueOutput across the five BEs + all parts +
   settings helpers, Optional CompoundTag getters. Gametest NBT round-trip
   tests keep this honest once tests run again.
3. Gametest adapter (unblocks the regression suite early - everything after
   this gets tested as it ports): adapter type + entry lists, getBlockEntity
   type args, assert-exception fixes. Structure templates (.nbt/.snbt) should
   DFU-upgrade on load; verify empty5/empty12 actually do.
4. Parts + part models against AE2's client model classes; mesh endpoint
   transport handlers onto the resource/transfer API.
5. Screens/client: InitScreens, render-state model, mouse events,
   EndpointHighlighter + TracePanel renderers, paletted_permutations JSON
   format check (gen_textures.py output may need a schema bump).
6. JEI: restore a 26.1 JEI artifact if one exists, else stub JeiIntegration
   behind a loaded-check.
7. `make data` regen, guide pages sanity (GuideME 26.1), `make test` green,
   then re-enable compat suite entries as their ports appear.

## Progress log

- 2026-08-12 bootstrap: toolchain + deps resolve end to end; 3098 errors inventoried.
- 2026-08-12 mechanical sweeps, 3098 -> 1756: Identifier rename everywhere
  (incl. buf read/writeIdentifier and IdentifierArgument), registry `get()` ->
  `getValue()`, `isClientSide()`, `appeng.util.Icon` import,
  `Capabilities.Item/Fluid/Energy`, `FMLEnvironment.isProduction()`,
  `new BlockEntityType<>(...)`, `sendSystemMessage`/`sendOverlayMessage`,
  `dimension().identifier()`, `ContainerInput`, and gametest
  `getBlockEntity(pos, BlockEntity.class)` type witnesses. The remaining 1756
  are the structural clusters: gametest adapter (~450, incl. the
  `GameTestAssertException(Component, int)` ctors), part models (~330),
  ValueInput/ValueOutput serialization (~250), GUI/client (~150), transfer-API
  handler types on the mesh, JEI, misc signatures.
- Signature lookup of record while porting:
  `build/moddev/artifacts/minecraft-patched-26.1.2.95-sources.jar` (the patched
  vanilla sources MDG builds locally) - grep it before guessing any vanilla API.

## Open decisions

- Adopt AE2's `src/client/java` client source-set split, or keep a single
  source set with Dist guards? The split matches upstream and the 26.1 client
  jar model; it also reshuffles every client class path. Lean: adopt the split
  during step 5.
- Mesh endpoint energy/item/fluid handlers on the transfer API may change
  observable batching semantics - re-read the mesh gametests' expectations
  before "fixing" them to pass.
- Branch versioning: keep mod_version in lockstep with main (currently 0.34.0)
  and let the jar's `26.1.2-` prefix disambiguate, or fork the version line?
  Currently lockstep.
