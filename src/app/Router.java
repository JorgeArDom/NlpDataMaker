package app;

import java.io.File;

public class Router {

	public static String pathDatoDocumento(String archivo) {
		return pathArchivo("Documentos", archivo);
	}

	public static String pathDatoRegistro(String archivo) {
		return pathArchivo("registros", archivo);
	}

	public static String pathDatoAtributos(String archivo) {
		return pathArchivo("atributos", archivo);
	}

	public static String pathDatoPreguntas(String archivo) {
		return pathArchivo("preguntas", archivo);
	}
	
	public static String pathDatoNpl(String archivo) {
		return pathArchivo("npl", archivo);
	}

	public static String pathDatoEquivalencias(String archivo) {
		return pathArchivo("equivalencias", archivo);
	}

	public static String pathDato(String archivo) {
		return pathArchivo("", archivo);
	}

	public static String pathDatoPreferencias(String archivo) {
		return pathArchivo("registros", archivo);
	}
	
	public static String pathArchivo(String carpeta, String archivo) {
		return pathDir(carpeta) + archivo;
	}

	public static String pathDir(String carpeta) {
		String pathActual = new File("").getAbsolutePath().replace('\\', '/');
		return pathActual + "/resources/" + carpeta + "/";
	}

}
