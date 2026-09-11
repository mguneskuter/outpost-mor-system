# FX fixture

This raw CSV is a tracked demonstration of ECB daily reference observations.
It was retrieved on 2026-09-11 from https://data-api.ecb.europa.eu/service/data/EXR/D.CZK+DKK+GBP+HUF+PLN+RON+SEK+USD.EUR.SP00.A?format=csvdata&startPeriod=2026-09-07&endPeriod=2026-09-11.

The `TIME_PERIOD` field is the observation date. `CURRENCY` is the quoted
currency and `OBS_VALUE` is quoted currency units per EUR. The generator
derives ordered cross rates from these EUR observations. Generated rows
reference currencies by the ids defined in `com.outpost.common.iso.Currencies`:
CZK=1, DKK=2, EUR=3, GBP=4, HUF=5, PLN=6, RON=7, SEK=8, USD=9.

SHA-256: 6cb0fd2d12fa9c523f0972be248a6826a75674f5f4a76644405f01a4157f6b91.
