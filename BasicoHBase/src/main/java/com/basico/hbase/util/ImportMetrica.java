package com.basico.hbase.util;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;

/**
 * Importar los datos usando mapreduce para generar un modelo como el indicado
 * abajo
 * <p>
 * Modelo de datos
 * <li>Rowkey: mercado/modelo_probabilistico/timestamp/num_secuencial
 * <li>Column Family: modelo_probabilistico
 * <li>Qualifiers:
 * <li>empresa
 * <li>probabilidad_de_desplome
 * <li>Value: empresa/probabilidad_de_desplome
 * <p>
 * Cabecera de la tabla a importar:
 * <li>exchange
 * <li>stock_symbol
 * <li>date
 * <li>stock_price_open
 * <li>stock_price_high
 * <li>stock_price_low
 * <li>stock_price_close
 * <li>stock_volume
 * <li>stock_price_adj_close
 * 
 * @author Álvaro Sánchez Blasco
 */

public class ImportMetrica {

	// Elegimos un double por la precisión que ofrece. Lo añadiremos como parte
	// final de nuestra RK.
	private static double i = 0;

	private Configuration conf = null;
	private HBaseAdmin admin = null;

	private static Date dateFormateada;

	/*
	 * Clase mapper para hacer mapreduce con un fichero de entrada y una tabla
	 * de salida
	 */
	static class Map extends Mapper<LongWritable, Text, Text, Put> {

		protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

			// Obtiene una linea del fichero de log
			String messageStr = value.toString();
			// Omitimos la cabecera
			if (!messageStr.contains("exchange,")) {
				if (messageStr.contains(",")) {
					// separa por tabuladores los campos
					String[] logRecvArr = messageStr.split(",");
					if (logRecvArr.length != 9) {
						System.out.println("Linea incorrecta: " + messageStr);
						return;
					}

					// Recuperamos unicamente los valores que nos interesan
					String nombreMercado = logRecvArr[0];
					String nombreEmpresa = logRecvArr[1];
					String date = logRecvArr[2];
					// formato de entrada de la fecha
					SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

					try {
						dateFormateada = formatter.parse(date);
					} catch (ParseException e) {
						e.printStackTrace();
					}

					float spOpen = new Float(logRecvArr[3]);
					float spClose = new Float(logRecvArr[6]);

					float probability = getProbability(spOpen - spClose);

					// crea el objeto put
					Put put = new Put(Bytes.toBytes(nombreMercado + "/" + MisConstantes.modeloProbabilistico + "/"
							+ dateFormateada.getTime() + "/" + i), dateFormateada.getTime());

					// determina a que ColumnFamily pertenece la celda
					put.add(Bytes.toBytes(MisConstantes.columnFamily), Bytes.toBytes(MisConstantes.qualifierEmpresa),
							Bytes.toBytes(nombreEmpresa));
					// escribe el dato en el contexto
					context.write(new Text("1"), put);

					put.add(Bytes.toBytes(MisConstantes.columnFamily),
							Bytes.toBytes(MisConstantes.qualifierProbabilidad), Bytes.toBytes(probability));
					// escribe el dato en el contexto
					context.write(new Text("1"), put);
					i = i + new Double(0.001);

				} else {
					System.out.println("Formato incorrecto");
				}
			} else {
				// System.out.println("Cabecera de la tabla\n");
			}
		}
	}

	/**
	 * Crea los objetos de configuracion y admin para acceso a HBase
	 */
	private ImportMetrica() throws IOException {
		this.conf = HBaseConfiguration.create();
		this.admin = new HBaseAdmin(conf);
	}

	/**
	 * Crea la tabla
	 */
	private void creaTabla() throws IOException {

		// si existe la borramos
		if (admin.tableExists(MisConstantes.tableName)) {
			borraTabla();
		}

		// crea el descriptor de la tabla
		HTableDescriptor desc = new HTableDescriptor(TableName.valueOf(MisConstantes.tableName));
		// crea el descriptor de la ColumnFamily1
		HColumnDescriptor coldef1 = new HColumnDescriptor(MisConstantes.columnFamily);
		// set maxversions a 1 para ColumnFamily1
		coldef1.setMaxVersions(1);
		// actualiza el descriptor de la tabla
		desc.addFamily(coldef1);
		// crea la tabla
		admin.createTable(desc);
	}

	/**
	 * Funcion que permite construir el modelo estadístico
	 * 
	 * @param metric
	 *            metrica que define el modelo estadístico
	 * @return
	 */
	private static float getProbability(float openMinusClose) {

		if ((openMinusClose) < 0) {
			return (float) 0;
		} else if (((openMinusClose) >= 0) && (openMinusClose) < 10) {
			return (float) 0.2;
		} else if (((openMinusClose) >= 10) && (openMinusClose) < 20) {
			return (float) 0.5;
		} else if ((openMinusClose) >= 20) {
			return (float) 0.9;
		}

		return 0;
	}

	/**
	 * Ejecuta el job M/R
	 */
	private void execute() throws Exception {
		// construye el objeto job y le asigna los tipos de entrada y salida
		Job job = Job.getInstance();
		job.setJobName(this.getClass().getSimpleName());
		job.setJarByClass(ImportMetrica.class);

		// asigna los formatos de entrada y salida del job
		job.setInputFormatClass(TextInputFormat.class);

		// asigna la clase Map
		job.setMapperClass(Map.class);
		// indica que no hay reducer
		job.setNumReduceTasks(0);

		// asigna el fichero de entrada
		FileInputFormat.setInputPaths(job, new Path(MisConstantes.fileName));

		// Vincula este job a la tabla de HBase como salida
		TableMapReduceUtil.initTableReducerJob(MisConstantes.tableName, null, job);

		// lanza el job
		job.waitForCompletion(true);
	}

	/**
	 * Borra la tabla
	 */
	private void borraTabla() throws IOException {

		System.out.println("*** borraTabla ***");

		// deshabilita la tabla
		admin.disableTable(MisConstantes.tableName);
		// borra la tabla
		admin.deleteTable(MisConstantes.tableName);
	}

	public static void main(String[] args) throws Exception {

		ImportMetrica modelo = new ImportMetrica();

		modelo.creaTabla();
		modelo.execute();
	}
}
