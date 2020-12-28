package trainningDataMaker;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import app.Router;

public class DataTransform {
	
	public void guardarTexto(String archivoDataChunker, String oraciones) {
		Texto texto = obtenerOraciones(archivoDataChunker);
		BufferedWriter br=null;
		String archivoOraciones=Router.pathDatoNpl(oraciones);
		String oracion = new String();
		try {
			br = new BufferedWriter(new FileWriter(archivoOraciones,false));
			for(int i=0;i<texto.listaOraciones.size();i++) {
				for(int j=0;j<texto.listaOraciones.get(i).palabras.size();j++) {
					String palabra = texto.listaOraciones.get(i).palabras.get(j);
					oracion+=palabra;
					if(j!=texto.listaOraciones.get(i).palabras.size()) {			
					oracion+=" ";
					}			
				}	
				br.write(oracion);
				br.newLine();
				oracion = new String ();
			}
		}catch (IOException e) {
			e.printStackTrace();
		}finally {
			try {
				br.close();
			} catch (IOException e) {
				System.out.println("No se pudo cerrar el lector");
			}
		}
	}

	public Texto obtenerOraciones(String archivoDataChunker) {
		BufferedReader br=null;
		String linea;
		String[] palabras = null;
		Oraciones oracion = new Oraciones();
		Texto texto = new Texto();
		String fileDataChunker=Router.pathDatoNpl(archivoDataChunker);
		try {
			br = new BufferedReader(new FileReader(fileDataChunker));
			while((linea=br.readLine())!=null) {
				palabras = linea.split(" ");
				if(palabras.length!=1) {	
					NplData npl = new NplData();
					npl.token=palabras[0];
					npl.tag=palabras[1];
					npl.chunk=palabras[2];
					oracion.listaChunkeada.add(npl);
					oracion.palabras.add(palabras[0]);
				}else {
					texto.listaOraciones.add(oracion);
					oracion = new Oraciones();
				}	
			}				
		}catch (FileNotFoundException e) {
			System.out.println("No se encontro el archivo");
		} catch (IOException e) {
			e.printStackTrace();
		}finally {
			try {
				br.close();
			} catch (IOException e) {
				System.out.println("No se pudo cerrar el lector");
			}
		}
		return texto;	
	}
}
