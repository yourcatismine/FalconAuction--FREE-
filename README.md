# Falcon Auction

A standalone auction house with configurable menus, item search, category filters, listing confirmations, expired-item returns, transaction history and administrator tools.

## Install

1. Install FalconAuction.jar in your server's plugins folder.
2. Install one supported economy setup: EssentialsX + Vault, or CoinsEngine and its required dependencies.
3. Select the provider in plugins/FalconAuction/config.yml and restart the server.

Default configuration:

```yaml
economy:
  provider: ESSENTIALSX
  coinsengine-currency: coins
```

Set provider to COINSENGINE to use a CoinsEngine currency. The currency ID must exist. EssentialsX uses its own Vault provider explicitly. Unsupported or unavailable providers stop the auction from enabling rather than charging another wallet.

## Commands

- /ah — open the auction house.
- /ah sell <price> — list the item in your hand after confirmation.
- /ah <search> — search listings.
- /auction reload — reload settings, menus, messages, colors and filters.
- /auction player <name> — open player auction administration.

Prices accept k/m/b/t suffixes. Menu settings control listing duration, maximum price, maximum active listings and disabled materials.

## Permissions

- auction.reload — reload configuration; default: operators.
- auction.admin — administrator menus; default: operators.
- auction.quickbuy — allow quick purchases when the player's settings.quick_auction_buy preference is enabled; default: false.
- donutauction.reload — retained reload permission alias.

## Configuration and data

- config.yml — economy provider and theme colors. It also accepts settings/menu/message overrides.
- economy/auction/config.yml — auction settings and menu layouts.
- economy/auction/filter.yml — category filters.
- messages/economy/auction.yml — messages and sounds.
- players/<uuid>.yml — auction preferences and local history.
- data/server/auctions/ — this plugin's listings, transactions and pending seller payments.

/auction reload keeps existing listings intact. Invalid YAML leaves the active settings in place and reports an error. Provider changes require a full restart. The plugin does not import data or settings from another plugin and does not create a survival/tools folder. Item metadata and lore are preserved.

Offline sale payments are stored until the seller joins. If an external economy transaction is interrupted after payment begins, the pending record is kept in paying state for reconciliation instead of retrying an uncertain payment automatically.

## Development

Java sources under `src/main/java/com/falcon/auction/` are grouped by responsibility:

- `FalconAuction.java` — plugin entry point.
- `auction/` — auction controller, listings and manager.
- `command/` — player and administrator commands.
- `config/` — configuration loading and theme settings.
- `economy/` — economy providers and Vault integration.
- `gui/` — menus, listeners and sign input.
- `player/` — player preferences and data.
- `scheduler/` — Paper/Folia scheduling adapter.
- `storage/` — file persistence and item serialization.
- `transaction/` — transaction records and manager.
- `util/` — shared formatting and utility methods.

Build from this directory with mvn clean package. Output: target/FalconAuction.jar. The project currently compiles against Paper API 26.1.2.build.61-stable; use the JDK required by that API. Paper/Folia scheduling is supported by the adapter. Test the intended Minecraft/server and economy-plugin versions on a staging server before publishing compatibility claims.

Automated checks cover configuration reloads, rejected malformed YAML, color formatting and provider selection. Live GUI and third-party economy integration checks are still needed.
