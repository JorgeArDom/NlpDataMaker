package app;

import trainningDataMaker.DataTransform;

public class TrainingDataTester {

	public static void main(String[] args) {
		
		/*
		 * una conversación tiene varias preguntas
		 * 	una pregunta tiene muchas sentences
		 * 		las sentences tienen muchos chunks
		 * 			los chunks tienen muchas palabras
		 * 				palabras (tokens) tiene un tag
		 */
		
		DataTransform unaTransformacion = new DataTransform();
		//obtener oraciones de archivos taggeados y chunkenizados y guardarlos en el archivo oraciones.txt
		unaTransformacion.guardarTexto("trainDataChunker.txt","oraciones.txt");				
	}

}
