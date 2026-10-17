"""Saves every OpenStreetMap ATM and bank in Sri Lanka to data/osm_banking_sri_lanka.json.

The API serves Nearby ATMs and banks in Sri Lanka from that file because the public Overpass
servers are often at capacity. Run this now and then to pick up new places:

    python backend/scripts/fetch_osm_banking.py

Data © OpenStreetMap contributors, ODbL (https://www.openstreetmap.org/copyright).
"""

import json
import time
from pathlib import Path

import httpx

OUTPUT = Path(__file__).resolve().parent.parent / "data" / "osm_banking_sri_lanka.json"
QUERY = (
    '[out:json][timeout:180];area["ISO3166-1"="LK"][admin_level=2]->.lk;'
    'nwr["amenity"~"^(atm|bank)$"](area.lk);out center tags;'
)
SERVERS = (
    "https://overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
)
# Only what the API's osm_place() reads.
KEEP_TAGS = {"amenity", "name", "brand", "operator", "atm", "addr:street", "addr:city", "opening_hours"}


def fetch() -> dict:
    """Tries each server in turn, for a few rounds; they are often busy."""
    for _ in range(10):
        for url in SERVERS:
            try:
                response = httpx.post(url, data={"data": QUERY}, timeout=200,
                                      headers={"User-Agent": "PayCeylon/1.0 (university demo)"})
                if response.status_code == 200:
                    return response.json()
                print(f"{url}: HTTP {response.status_code}")
            except httpx.HTTPError as error:
                print(f"{url}: {type(error).__name__}")
        time.sleep(20)
    raise SystemExit("Every Overpass server is busy; try again later.")


def main() -> None:
    raw = fetch()
    elements = []
    for element in raw["elements"]:
        point = element.get("center", element)
        if "lat" not in point:
            continue
        elements.append({
            "type": element["type"],
            "id": element["id"],
            "lat": round(point["lat"], 6),
            "lon": round(point["lon"], 6),
            "tags": {key: value for key, value in element.get("tags", {}).items() if key in KEEP_TAGS},
        })
    document = {
        "source": "OpenStreetMap contributors, ODbL (https://www.openstreetmap.org/copyright), via Overpass API",
        "area": "Sri Lanka",
        "osm_base": raw.get("osm3s", {}).get("timestamp_osm_base"),
        "elements": elements,
    }
    OUTPUT.write_text(json.dumps(document, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"Saved {len(elements)} ATMs and banks to {OUTPUT}")


if __name__ == "__main__":
    main()