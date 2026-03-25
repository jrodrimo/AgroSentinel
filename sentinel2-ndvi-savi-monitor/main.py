"""
Olive Grove Monitor: Downloads the latest cloud-free Sentinel-2 image,
computes NDVI/SAVI locally and generates maps and reports.
"""
import os
import sys
import shutil
from datetime import datetime, timedelta

import numpy as np
import matplotlib.pyplot as plt
import matplotlib.colors as mcolors
import matplotlib.patches as mpatches
import geopandas as gpd
from dotenv import load_dotenv
from sentinelhub import (
    SHConfig, SentinelHubRequest, SentinelHubCatalog, DataCollection,
    MimeType, CRS, Geometry, bbox_to_dimensions
)

# Configuration 
PARCELA = "maqueda_olivos"
NUBES_MAX = 0.20
RESOLUCION = 10

NDVI_UMBRALES = [0.0, 0.20, 0.35, 0.50, 0.65, 1.0]
NDVI_ETIQUETAS = ["Critical/Bare soil", "Severe stress", "Moderate stress", "Good condition", "Excellent vigour"]
SAVI_UMBRALES = [0.0, 0.10, 0.20, 0.35, 0.50, 1.0]
SAVI_ETIQUETAS = ["Critical/Bare soil", "Very low cover", "Medium cover", "Good cover", "Excellent cover"]

COLORES = ["#8B0000", "#d7191c", "#fdae61", "#a6d96a", "#1a9641"]
CMAP = mcolors.ListedColormap(COLORES)
CMAP.set_bad("white", 0)

# Downloads the 12 Sentinel-2 bands + dataMask for a full TIFF
EVALSCRIPT = """//VERSION=3
function setup() { return {input: ["B01","B02","B03","B04","B05","B06","B07","B08","B8A","B09","B11","B12","dataMask"], output: {bands: 13, sampleType: "FLOAT32"}}; }
function evaluatePixel(s) { return [s.B01, s.B02, s.B03, s.B04, s.B05, s.B06, s.B07, s.B08, s.B8A, s.B09, s.B11, s.B12, s.dataMask]; }
"""

# -- Utilities -----------------------------------------------------------------
def clasificar(valor, umbrales, etiquetas):
    """Returns the label corresponding to a value based on the thresholds."""
    for i in range(len(umbrales) - 1):
        if umbrales[i] <= valor < umbrales[i + 1]:
            return etiquetas[i]
    return etiquetas[-1]

def configurar_ejes(ax, datos):
    """Sets up a pixel-by-pixel grid and hides axis labels."""
    ax.set_xticks(np.arange(-0.5, datos.shape[1], 1), minor=True)
    ax.set_yticks(np.arange(-0.5, datos.shape[0], 1), minor=True)
    ax.grid(which="minor", color="black", linewidth=0.5)
    ax.tick_params(bottom=False, left=False, labelbottom=False, labelleft=False)

def anotar_valores(ax, datos, umbral_color_blanco):
    """Writes numeric values on each pixel if the map is small enough."""
    if datos.size > 400:
        return
    for (i, j), valor in np.ndenumerate(datos):
        if not np.isnan(valor):
            color = "white" if valor < umbral_color_blanco else "black"
            ax.text(j, i, f"{valor:.2f}", ha="center", va="center",
                    fontsize=6, fontweight="bold", color=color)

# API and Catalog 
def inicializar_api():
    """Loads credentials and geometry. Returns config, geometry, dimensions, collection, catalog."""
    load_dotenv()
    cid = os.environ.get("SH_CLIENT_ID")
    csec = os.environ.get("SH_CLIENT_SECRET")
    if not cid or not csec:
        sys.exit("ERROR: Missing SH_CLIENT_ID or SH_CLIENT_SECRET in the .env file")

    config = SHConfig()
    config.sh_client_id = cid
    config.sh_client_secret = csec
    config.sh_token_url = "https://identity.dataspace.copernicus.eu/auth/realms/CDSE/protocol/openid-connect/token"
    config.sh_base_url = "https://sh.dataspace.copernicus.eu"

    base_dir = os.path.dirname(os.path.abspath(__file__))
    ruta_geojson = os.environ.get("GEOJSON_PATH", os.path.join(base_dir, "maqueda_olivos.geojson"))
    if not os.path.exists(ruta_geojson):
        sys.exit(f"ERROR: GeoJSON file not found: {ruta_geojson}")

    geometria = Geometry(gpd.read_file(ruta_geojson).geometry.values[0], crs=CRS.WGS84)
    dimensiones = bbox_to_dimensions(geometria.bbox, resolution=RESOLUCION)

    nombre_col = "S2L2A"
    existentes = [c.name for c in DataCollection.get_available_collections()]
    if nombre_col in existentes:
        coleccion = DataCollection[nombre_col]
    else:
        coleccion = DataCollection.define(nombre_col, api_id="sentinel-2-l2a", service_url=config.sh_base_url)

    catalogo = SentinelHubCatalog(config=config)
    return config, geometria, dimensiones, coleccion, catalogo

def buscar_mejor_fecha(catalogo, geometria):
    """Queries the catalog and returns the most recent date with clouds <= NUBES_MAX."""
    hoy = datetime.now()
    inicio = (hoy - timedelta(days=30)).strftime("%Y-%m-%d")
    fin = hoy.strftime("%Y-%m-%d")

    resultados = catalogo.search(
        collection="sentinel-2-l2a",
        geometry=geometria,
        time=(inicio, fin)
    )

    fechas_validas = []
    for feature in resultados:
        nubes = feature["properties"].get("eo:cloud_cover", 100)
        if nubes <= (NUBES_MAX * 100):
            fechas_validas.append(feature["properties"]["datetime"][:10])

    if not fechas_validas:
        return None
    return sorted(fechas_validas, reverse=True)[0]

def descargar_multiespectral(config, coleccion, geometria, dimensiones, carpeta, fecha_exacta):
    """Downloads the bands for exactly the specified day and keeps the original GeoTIFF."""
    carpeta_tmp = os.path.join(carpeta, "_tmp")
    os.makedirs(carpeta_tmp, exist_ok=True)

    # Inclusive range: [date, date+1day) to ensure the API covers the entire day
    fecha_fin = (datetime.strptime(fecha_exacta, "%Y-%m-%d") + timedelta(days=1)).strftime("%Y-%m-%d")
    destino_tiff = os.path.join(carpeta, f"{PARCELA}_bandas_{fecha_exacta}.tiff")

    try:
        peticion = SentinelHubRequest(
            evalscript=EVALSCRIPT,
            input_data=[SentinelHubRequest.input_data(
                data_collection=coleccion,
                time_interval=(fecha_exacta, fecha_fin),
                maxcc=NUBES_MAX
            )],
            responses=[SentinelHubRequest.output_response("default", MimeType.TIFF)],
            geometry=geometria, size=dimensiones, config=config, data_folder=carpeta_tmp
        )
        # save_data=True stores the TIFF in a single request (avoids double download)
        datos_numpy = peticion.get_data(save_data=True)[0]

        # Copy the original GeoTIFF (12 bands + dataMask) into the results folder
        tiff_copiado = False
        archivos = peticion.get_filename_list()
        for ruta in archivos:
            if os.path.exists(ruta):
                shutil.copy2(ruta, destino_tiff)
                tiff_copiado = True
                break

        # Fallback: search for any .tiff in the temp folder
        if not tiff_copiado:
            import glob
            for ruta in glob.glob(os.path.join(carpeta_tmp, "**", "*.tiff"), recursive=True):
                shutil.copy2(ruta, destino_tiff)
                tiff_copiado = True
                break

        if tiff_copiado:
            print(f"      -> GeoTIFF saved: {os.path.basename(destino_tiff)}")
        else:
            print("      -> WARNING: Could not save the original GeoTIFF.")

        return datos_numpy

    except Exception as error:
        print(f"ERROR during download: {error}")
        return None
    finally:
        shutil.rmtree(carpeta_tmp, ignore_errors=True)

#Vegetation Index Calculation 
def calcular_indices_vegetacion(datos):
    """Computes NDVI and SAVI locally using NumPy."""
    # Order: 0=B01,1=B02,2=B03,3=B04,4=B05,5=B06,6=B07,7=B08,8=B8A,9=B09,10=B11,11=B12,12=dataMask
    banda4, banda8 = datos[:, :, 3], datos[:, :, 7]
    mascara_valida = datos[:, :, 12] == 1

    with np.errstate(divide="ignore", invalid="ignore"):
        ndvi_calc = (banda8 - banda4) / (banda8 + banda4)
        savi_calc = ((banda8 - banda4) / (banda8 + banda4 + 0.5)) * 1.5
 #L in savi is 0.5 in this project at the moment
    return np.where(mascara_valida, ndvi_calc, np.nan), np.where(mascara_valida, savi_calc, np.nan)

# Map Export 
def exportar_mapa_indice(datos, umbrales, etiquetas, titulo, ruta_salida):
    """Generates a coloured PNG with legend (including threshold ranges) and per-pixel values."""
    norma = mcolors.BoundaryNorm(umbrales, CMAP.N)
    fig, ax = plt.subplots(figsize=(9, 9))
    ax.imshow(datos, cmap=CMAP, norm=norma, interpolation="nearest")
    configurar_ejes(ax, datos)
    anotar_valores(ax, datos, umbrales[2])
    ax.set_title(titulo, fontsize=12, fontweight="bold")
    ax.text(0.5, 1.01, "Custom geometry (user-provided parcel)", transform=ax.transAxes,
            ha="center", fontsize=8, fontstyle="italic", color="gray")

    # Build legend labels with threshold range references, e.g. "[0.00 - 0.20] Severe stress"
    etiquetas_con_rango = [
        f"[{umbrales[i]:.2f} – {umbrales[i+1]:.2f}] {etiquetas[i]}"
        for i in range(len(etiquetas))
    ]
    leyenda = [mpatches.Patch(facecolor=c, edgecolor="black", label=e)
               for c, e in zip(COLORES, etiquetas_con_rango)]
    ax.legend(handles=leyenda, loc="upper left", bbox_to_anchor=(1.02, 1), fontsize=9)
    fig.savefig(ruta_salida, bbox_inches="tight", dpi=150, transparent=True)
    plt.close(fig)



# Text Report
def exportar_informe_texto(ndvi, savi, carpeta_salida, fecha):
    """Generates a TXT with the status of each pixel and a summary with alerts."""
    alertas, lista_pixeles = [], []

    for (i, j), v_ndvi in np.ndenumerate(ndvi):
        if np.isnan(v_ndvi):
            continue
        v_savi = savi[i, j]
        estado_ndvi = clasificar(v_ndvi, NDVI_UMBRALES, NDVI_ETIQUETAS)
        estado_savi = clasificar(v_savi, SAVI_UMBRALES, SAVI_ETIQUETAS)

        datos_pixel = {"Row": i, "Col": j, "NDVI": v_ndvi, "e_NDVI": estado_ndvi, "SAVI": v_savi, "e_SAVI": estado_savi}
        lista_pixeles.append(datos_pixel)

        if v_ndvi < NDVI_UMBRALES[2] or v_savi < SAVI_UMBRALES[2]:
            alertas.append(datos_pixel)

    ruta_txt = os.path.join(carpeta_salida, f"{PARCELA}_informe_{fecha}.txt")
    with open(ruta_txt, "w", encoding="utf-8") as f:
        f.write(f"REPORT {PARCELA} (Image from {fecha}) - Resolution {RESOLUCION}m\n{'='*78}\n")
        f.write(f"{'PIXEL':>10}  {'NDVI':>7}  {'NDVI STATUS':<25}  {'SAVI':>7}  {'SAVI STATUS':<25}\n{'-'*78}\n")

        for px in lista_pixeles:
            coordenada = f"({px['Row']},{px['Col']})"
            f.write(f"{coordenada:>10}  {px['NDVI']:>7.4f}  {px['e_NDVI']:<25}  {px['SAVI']:>7.4f}  {px['e_SAVI']:<25}\n")

        if not lista_pixeles:
            f.write("\nNO DATA - possible full cloud cover.\n")
        else:
            valores_ndvi = [px["NDVI"] for px in lista_pixeles]
            valores_savi = [px["SAVI"] for px in lista_pixeles]
            f.write(f"\nSUMMARY: {len(lista_pixeles)} px | Mean NDVI={np.mean(valores_ndvi):.4f} | Mean SAVI={np.mean(valores_savi):.4f}\n")
            if alertas:
                f.write(f"ALERTS: {len(alertas)} critical pixels\n")
                for a in alertas:
                    f.write(f"  ({a['Row']},{a['Col']}) NDVI={a['NDVI']:.4f} [{a['e_NDVI']}] SAVI={a['SAVI']:.4f} [{a['e_SAVI']}]\n")

    return alertas, lista_pixeles

# Main 
def main():
    print(f"=== OLIVE GROVE MONITOR {PARCELA} ===")

    print("[1/5] Connecting to the API...")
    config, geometria, dimensiones, coleccion, catalogo = inicializar_api()

    print(f"[2/5] Searching for the latest image with less than {NUBES_MAX*100:.0f}% cloud cover...")
    fecha_optima = buscar_mejor_fecha(catalogo, geometria)
    if not fecha_optima:
        sys.exit("Error: No valid image found in the last 30 days.")
    print(f"      -> Found: {fecha_optima}")

    carpeta_resultados = os.path.join(os.path.dirname(__file__), "resultados", fecha_optima)
    os.makedirs(carpeta_resultados, exist_ok=True)

    print(f"[3/5] Downloading image from {fecha_optima}...")
    datos_satelite = descargar_multiespectral(config, coleccion, geometria, dimensiones, carpeta_resultados, fecha_optima)
    if datos_satelite is None:
        sys.exit("Fatal error: Could not download the image.")
    print(f"      -> {datos_satelite.shape[0]}x{datos_satelite.shape[1]} px, {datos_satelite.shape[2]} bands (12 spectral + dataMask)")

    print("[4/5] Computing vegetation index..")
    ndvi, savi = calcular_indices_vegetacion(datos_satelite)
    pixeles_validos = int(np.count_nonzero(~np.isnan(ndvi)))
    if pixeles_validos == 0:
        print("      -> WARNING: 0 valid pixels (possible cloud cover).")

    print("[5/5] Exporting maps and report...")
    exportar_mapa_indice(ndvi, NDVI_UMBRALES, NDVI_ETIQUETAS, f"NDVI - {fecha_optima}",
                         os.path.join(carpeta_resultados, f"{PARCELA}_ndvi_{fecha_optima}.png"))
    exportar_mapa_indice(savi, SAVI_UMBRALES, SAVI_ETIQUETAS, f"SAVI - {fecha_optima}",
                         os.path.join(carpeta_resultados, f"{PARCELA}_savi_{fecha_optima}.png"))
    alertas_encontradas, total_pixeles = exportar_informe_texto(ndvi, savi, carpeta_resultados, fecha_optima)

    media_ndvi = float(np.nanmean(ndvi)) if pixeles_validos else 0
    media_savi = float(np.nanmean(savi)) if pixeles_validos else 0

    print(f"\nFINAL SUMMARY ({fecha_optima})")
    print(f"  {len(total_pixeles)} px | NDVI={media_ndvi:.4f} [{clasificar(media_ndvi, NDVI_UMBRALES, NDVI_ETIQUETAS)}] | SAVI={media_savi:.4f} [{clasificar(media_savi, SAVI_UMBRALES, SAVI_ETIQUETAS)}]")
    print(f"  {len(alertas_encontradas)} critical alerts" if alertas_encontradas else "  Healthy terrain. No alerts.")
    print(f"  Results in: {carpeta_resultados}/")

if __name__ == "__main__":
    main()
