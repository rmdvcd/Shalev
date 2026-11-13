# Vector de interoperabilidad GL

`dshalev-protocol-v1.json` es una copia literal del vector público del servicio GL
(`services/redeem/test/fixtures/dshalev-protocol-v1.json`, commit `ee55cb5`).

Contiene **solo claves públicas de prueba** y firmas de ejemplo. No sirve para
producción: el build no fija `servicePublicKey`, `secondaryPublicKey` ni ninguna
clave de este archivo. Su uso es comprobar que los bytes canónicos y las firmas
de DShalev coinciden con el contrato.
