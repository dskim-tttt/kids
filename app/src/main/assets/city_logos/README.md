# City imagery registry

The registry maps actual bundled images to exact city keys. Unregistered or invalid keys
show a neutral placeholder rather than pretending to have an official logo. Two kinds of
bundled imagery are supported, and the UI must distinguish them:

- `official`: a reviewed municipality emblem, labelled as a municipal logo.
- `original`: original app-owned travel artwork, labelled **여행 배지**. It is never an
  official emblem merely because it represents a city.

Place PNG or WebP files in this directory and register exact city keys in `manifest.json`.
Example schema (not an actual asset/source):

```json
{
  "version": 1,
  "entries": [
    {
      "key": "EXACT_COUNTRY|EXACT_ADMIN_AREA|EXACT_LOCALITY",
      "asset": "city_logos/exact_city.png",
      "sourceKind": "official",
      "sourceUrl": "https://official-source.example/logo-page",
      "license": "Actual permission/license and attribution requirements",
      "verified": true
    }
  ]
}
```

Original artwork uses `"sourceKind": "original"`, an ownership/license note in `license`,
and an optional HTTPS `sourceUrl`. It does not require the official-logo verification flag.

- The key must exactly match the app's country, administrative area and locality key.
  Names alone are ambiguous and never select an official logo.
- For official logos, check the originating municipality, current emblem version,
  permission/license and attribution before setting `verified` to `true`. The flag records
  a human review; it cannot itself prove provenance or permission.
- Keep original image proportions. The app bundles images and never downloads them at
  runtime. Convert a reviewed SVG to PNG/WebP rather than approximating it with generated art.
- Filenames are ASCII under `city_logos/`, without absolute paths, `..`, external URLs,
  backslashes or nested folders. Only lowercase `.png` and `.webp` extensions are accepted.
- Conflicting duplicate keys are omitted. Missing/corrupt images remain a placeholder.
- Official entries require HTTPS source, license text and verification. Original entries
  require explicit kind and an ownership/license note; a supplied source must be HTTPS.
- Add any required attribution to the app's credits before shipping.

## Shared sprite sheet

Multiple exact city keys may share one image using an optional `cell` array:

```json
"cell": [1, 2, 4, 3]
```

The values mean `[column, row, columns, rows]`, all zero-based for the selected column/row.
This example chooses column 1, row 2 of an equal 4-column, 3-row sheet. The image must have
precisely equal cells; any intended padding belongs inside each cell. The renderer draws
the source rectangle directly and does not create or save cropped image files.

Omitting `cell` uses the complete image. Grid sizes are 1–16 in each dimension, selected
indices must fit, and non-integer/invalid cells are rejected. Exact city aliases can use
separate records pointing to the same asset/cell. A source image is decoded once and shared
by its records, with a 1024px decode cap and a 4MB bitmap LRU cache.

## Included in 0.5.0

`original_travel_sheet.png` is an original illustrated souvenir atlas generated for this app.
It represents Seoul, Suwon, Yongin, Seongnam, Incheon, Chuncheon, Gangneung, Wonju, Daejeon, Sejong, Cheongju and Cheonan in row-major order.
It is not an official logo set and is not an exact architectural/landmark record. The UI labels it 여행 배지.
The manifest contains 12 canonical city keys from CityIdentity; new cities remain a neutral pending slot until registered.
