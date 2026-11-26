# FlowerConnect — Asset Credits & Licences

This document records the provenance, source, and licensing for local static and procedural assets utilized by the FlowerConnect application.

## Procedural Demo Images

All demo flower product images seeded in development are generated procedurally in-memory via `com.flowerconnect.demo.DemoImageGenerator`. No external images are downloaded, scraped, or hotlinked.

| Asset Identifier / Product Name | Generator Component | Dimensions & Format | Licence | Provenance / Author |
|---------------------------------|---------------------|---------------------|---------|---------------------|
| Velvet Red Roses                | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Sunny Meadow Sunflower Bouquet  | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Royal Purple Orchid Harmony     | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Blush Pink Carnation Delight    | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Golden Sunset Lily Bunch        | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Classic Mixed Spring Blossom    | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Lavender Grace Floral Basket    | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Coral Miniature Rose Posy       | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Pure Snow Daisy Bouquet         | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Midnight Blue Hydrangea Basket  | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Scarlet Romance Rose Bunch      | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| White Elegance Calla Lilies     | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Tuscan Sunflower Vase           | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Pastel Spring Bloom Box         | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Orchid & Fern Celebration       | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Golden Marigold Festive Garland | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Crimson Peony Silk Bundle       | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Exotic Purple Carnations        | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Sunburst Yellow Roses           | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Peach Blossom Posy              | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Pure Peace White Lilies         | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Autumn Glow Flower Box          | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Sweet Valentine Bunch           | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Serene Zen Floral Bowl          | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Vibrant Gerbera Burst           | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Petite Desktop Daisy            | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |
| Fragrant Night Jasmine Bunch    | `DemoImageGenerator.generateFlowerImage` | 400x400 JPEG | Apache-2.0 | FlowerConnect Project (Algorithmic Java2D Artwork) |

## Generation Implementation Details

- **Technology**: Java 2D Graphics (`java.awt.image.BufferedImage`, `javax.imageio.ImageIO`).
- **Rendering**: Smooth antialiased rendering with radial background shading, green natural stem and leaves, 12 rotatable floral petals with specular shading, central floral disc, and embedded product title label banner.
- **Output**: Clean JPEG bytes (`image/jpeg`) meeting the validation criteria of `ImageValidationService` (400x400 px, well within max 1600 px dimension, standard sRGB color space, and ~25 KB size well within the 5MB file size limit).
- **Licence**: Distributed under the terms of the Apache License, Version 2.0.

