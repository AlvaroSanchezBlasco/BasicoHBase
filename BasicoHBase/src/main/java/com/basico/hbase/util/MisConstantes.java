package com.basico.hbase.util;

/**
 * Clase de constantes comunes a los dos procesos, el de importación de datos, y
 * el de recuperación la máxima probabilidad de desplome para una fecha dada.
 * 
 * @author Álvaro Sánchez Blasco
 * */
public class MisConstantes {

	// Nombre de la tabla
	public static final String tableName = "TimestampEmpresaModeloMetrica";
	// Modelo probabilistico (puede ser parametrizable)
	public static final String modeloProbabilistico = "mod1";
	// Column Family de nuestra tabla (como sólo tenemos un modelo
	// probabilistico, asociamos el valor del mismo)
	public static final String columnFamily = modeloProbabilistico;
	// Qualifier para la empresa
	public static final String qualifierEmpresa = "emp";
	// Qualifier para la probabilidad de desplome
	public static final String qualifierProbabilidad = "prob";
	// Nombre del mercado de valores (parametrizable)
	public static final String nombreMercado = "NASDAQ";
	// Usaremos el fichero original para parsear nuestros datos:
	public static final String fileName = "data/NASDAQ_daily_prices_subset.csv";

}
