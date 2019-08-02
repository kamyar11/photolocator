# photolocator
Android application to view and modify image's location/geo data residing in EXIF

Version 2.5 changes:
- added the ability to modify image's geo data to a selected location on map;
- improved memory management and performance; now photos are only loaded to memory when they are about to be displayed and 'nulled' otherwise; now loading photos and finding photos happen in two different threads and perform faster;
