# Admin Overrides

This file is the **editable database** for the Mamsad app. The app fetches this file on every refresh and merges it with the live data from mamsad.ru.

## How it works

1. The Mamsad Android app fetches data from mamsad.ru via WordPress REST API
2. It also fetches `overrides.json` from this URL
3. The two are merged: **overrides take precedence** over mamsad.ru data

## What you can override per org

- `hidden: true` — hide the org from the app
- `featured: true` — pin to top of catalog
- `titleOverride` — replace title
- `excerptOverride` — replace short description
- `contentOverride` — replace full description
- `latOverride` / `lngOverride` — correct coordinates
- `addressOverride` — correct address
- `priceOverride` — correct price
- `ratingOverride` — correct rating
- `customTags` — extra tags to display on detail screen

## Adding custom orgs

Use `extraOrgs` to add brand-new kindergartens that are NOT on mamsad.ru:

```json
{
  "extraOrgs": [
    {
      "id": -1,
      "title": "Мой любимый садик",
      "excerpt": "Уютный домашний сад на 8 детей",
      "content": "<p>Полное описание...</p>",
      "cityName": "Королёв",
      "typeName": "Частный сад",
      "lat": 55.92,
      "lng": 37.85,
      "address": "г. Королёв, ул. Ленина 5",
      "priceFrom": "28000",
      "rating": 5.0,
      "phone": "+7 999 123-45-67",
      "site": "https://example.com",
      "customTags": ["Домашний", "Маленькие группы"]
    }
  ]
}
```

## Editing

**Don't edit this file directly in GitHub** — use the Admin Panel (web UI) at:

> https://mamsad-admin.vercel.app/

The admin panel uses the GitHub API to commit changes to this file. Just log in with the GitHub credentials and edit kindergartens through a friendly form.
