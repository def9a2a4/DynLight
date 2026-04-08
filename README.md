# DynLight

A dynamic entity-based lighting plugin for Paper 1.21+

Light sources follow players, illuminate dropped items, and make burning entities glow. Fully configurable, and provides an API for other plugins.

## Features

### Light Sources

- **Held Items** - Torches, lanterns, glowstone, and 40+ other items emit light when held
- **Dropped Items** - Items on the ground emit their light level
- **Burning Entities** - Any entity on fire glows with configurable intensity
- **Flaming Projectiles** - Arrows on fire, fireballs, and spectral arrows emit light
- **Enchanted Equipment** - Players wearing enchanted armor or holding enchanted items emit a subtle glow
- **Always-Lit Entities** - Blazes, Glow Squids, Magma Cubes, and Allays always emit light

### Special Mechanics

- **Water Sensitivity** - Fire-based items (torches, campfires) are extinguished underwater while waterproof items (glowstone, sea lanterns) continue to glow
- **Client-side only** - Lights are rendered client-side for optimal performance. Inspired by [DynamicLights](https://github.com/xCykrix/DynamicLights)
- **Per-Player Toggle** - Players can individually enable/disable dynamic lights client side
- **Async Rendering** - Light calculations run asynchronously for minimal server impact




# Configuration

Performance settings:
```yaml
# Update interval in ticks (20 ticks = 1 second)
# smaller values = more frequent updates = smoother light movement but higher CPU usage
update-interval: 1

# Maximum distance to render dynamic lights (blocks)
render-distance: 96

# How often to check for fire expiration on entities (ticks, 10 = 0.5 seconds)
fire-sweep-interval: 10
```

Enable or disable specific light source types:

```yaml
detection:
  held-items: true
  dropped-items: true
  burning-entities: true
  flaming-arrows: true
  enchanted-armor: true
  enchanted-items: true
  always-lit-entities: true
```

Configure light intensity for enchanted equipment:

```yaml
light-levels:
  enchanted-armor: 3
  enchanted-items: 3
```

Configure how items emit light:

```yaml
items:
  TORCH:
    light: 10
    water-sensitive: true
  LANTERN:
    light: 12
    water-sensitive: false
```

Water-sensitive items stop emitting light when submerged.


Configure how entities emit light when on fire:

```yaml
entities:
  # Properties: base-light, fire-light, horizontal-radius, height
  BLAZE:
    base-light: 8    # Always emits light
    fire-light: 15
```

| Property            | Description                                          | Default |
| ------------------- | ---------------------------------------------------- | ------- |
| `base-light`        | Light level when not on fire (0 = only when burning) | 0       |
| `fire-light`        | Light level when on fire                             | 12      |
| `horizontal-radius` | Light column radius (0=1x1, 1=3x3, 2=5x5)            | 1       |
| `height`            | Light column height in blocks                        | 2       |

The light column will once place one light block -- we search wider than you'd think to avoid obstacles, since light blocks can't be placed inside things like vines, tall grass, ladders, etc.

## Commands

| Command             | Description                         | Permission       |
| ------------------- | ----------------------------------- | ---------------- |
| `/dynlight help`    | Show available commands             | -                |
| `/dynlight enable`  | Enable dynamic lights for yourself  | `dynlight.use`   |
| `/dynlight disable` | Disable dynamic lights for yourself | `dynlight.use`   |
| `/dynlight info`    | Show active light source count      | `dynlight.admin` |
| `/dynlight stats`   | Show detailed statistics            | `dynlight.admin` |
| `/dynlight reload`  | Reload configuration                | `dynlight.admin` |
| `/dynlight regen`   | Regenerate all light sources        | `dynlight.admin` |


# API

Other plugins can register custom entities as light sources:

```java
// Get the API
DynLightAPI api = DynLightPlugin.getAPI();

// Register a light source
api.addLightSource(entity, 10);

// Update light level
api.updateLightLevel(entity, 12);

// Check if entity is a light source
boolean isLight = api.isLightSource(entity);
int level = api.getLightLevel(entity);

// Remove light source
api.removeLightSource(entity);

// Bulk operations
api.addLightSources(entityCollection, 8);
api.addLightSources(Map.of(entity1, 10, entity2, 5));
api.removeLightSources(entityCollection);
```

### Advanced: Custom Light Placement

Control how the light block is placed around the entity:

```java
// Custom radius and height for light placement search
LightSourceInfo info = LightSourceInfo.of(12, 2, 3); // level 12, radius 2, height 3
api.addLightSource(entity, info);

// Query full light info
LightSourceInfo current = api.getLightSourceInfo(entity);
```

### Advanced: Entity Light Detectors

Register a detector to automatically evaluate entities on chunk load and entity spawn:

```java
// Register a detector that makes all Endermen glow
api.registerDetector(entity -> {
    if (entity.getType() == EntityType.ENDERMAN) {
        return LightSourceInfo.of(8);
    }
    return null; // not a light source
});

// Manually scan entities with registered detectors
api.scanEntities(entities);

// Unregister when done
api.unregisterDetector(detector);
```


# Performance

DynLight is designed for performance:

- Async light computation prevents main thread blocking
- Event-driven entity tracking minimizes polling
- Equipment caching reduces repeated enchantment checks
- World-partitioned storage for efficient lookups
- Configurable render distance and update intervals


# Related plugins/projects

- https://github.com/xCykrix/DynamicLights
- https://modrinth.com/plugin/smooth-dynamic-light
- https://modrinth.com/plugin/simple-dynamic-light
- https://modrinth.com/datapack/dynamic-lights