package com.basico.hbase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.BinaryComparator;
import org.apache.hadoop.hbase.filter.CompareFilter;
import org.apache.hadoop.hbase.filter.FamilyFilter;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.util.Bytes;

import com.basico.hbase.util.MisConstantes;

/**
 * Objetivo: Calcular la probabilidad de caida de la
 * cotización de una empresa dado un modelo de probabilidades basado en la
 * diferencia open-close
 * 
 * @author Álvaro Sánchez Blasco
 */

public class EstimateMarketDrop {

	// formato de entrada de la fecha
	private static final SimpleDateFormat formatter = new SimpleDateFormat(
			"dd/MM/yyyy");

	// variables para acceder a HBase
	private Configuration conf = null;

	private static String dateInString;

	// instanciamos las clases necesarias para acceder a HBase en el constructor
	// dado que son threadsafe
	public EstimateMarketDrop() throws IOException {
		this.conf = HBaseConfiguration.create();
	}

	/**
	 * Busca la row con el maximo value para el qualifier 'prob'
	 * 
	 * @param dateStart
	 *            dia de busqueda
	 * @param statModel
	 *            qualifier para buscar dentro del modelo estadístico
	 * @return
	 * @throws IOException
	 */
	private Hashtable<Float, List<String>> getMetric(Date dateStart)
			throws IOException {

		// Calcula la fecha de fin sumando un dia
		Calendar c = Calendar.getInstance();
		c.setTime(dateStart);
		c.add(Calendar.DAY_OF_MONTH, 1);
		Date dateEnd = c.getTime();

		// Creamos la instancia para acceder por RPC a HBase
		HTable tabla = new HTable(conf, MisConstantes.tableName);

		// crea al array de filtros
		List<Filter> filters = new ArrayList<Filter>();

		// Crear los filtros adecuados, y lanzar el scan, para llegar al
		// resultado deseado
		// Creamos un objeto scan con start y stop rows, ya que nuestro modelo
		// está preparado para este tipo de búsquedas.
		Scan scan = new Scan(Bytes.toBytes(MisConstantes.nombreMercado + "/"
				+ MisConstantes.modeloProbabilistico + "/"
				+ dateStart.getTime()),
				Bytes.toBytes(MisConstantes.nombreMercado + "/"
						+ MisConstantes.modeloProbabilistico + "/"
						+ dateEnd.getTime()));

		// Crea el filtro para ColumnFamily. Lo añadimos porque como puede ser
		// parametrizable, hay que tenerlo en cuenta.
		Filter filterCF = new FamilyFilter(CompareFilter.CompareOp.EQUAL,
				new BinaryComparator(Bytes.toBytes(MisConstantes.columnFamily)));
		filters.add(filterCF);

		// crea la lista de filtros con AND
		FilterList filterList = new FilterList(
				FilterList.Operator.MUST_PASS_ALL, filters);

		// asociamos al scan la lista de filtros
		scan.setFilter(filterList);

		// obtenemos el iterador sobre la lista de resultados
		ResultScanner scanner = tabla.getScanner(scan);

		// iteramos la lista de resultados (rango de RK)
		Hashtable<Float, List<String>> resultados = new Hashtable<Float, List<String>>();

		// si ha encontrado un resultado lo mete en la lista
		for (Result result : scanner) {
			Cell ke = result.getColumnLatestCell(
					Bytes.toBytes(MisConstantes.columnFamily),
					Bytes.toBytes(MisConstantes.qualifierEmpresa));

			// captura el value asociado a la celda
			String sEmpresa = Bytes.toString(CellUtil.cloneValue(ke));

			Cell kp = result.getColumnLatestCell(
					Bytes.toBytes(MisConstantes.columnFamily),
					Bytes.toBytes(MisConstantes.qualifierProbabilidad));

			// captura el value asociado a la celda
			float metric = Bytes.toFloat(CellUtil.cloneValue(kp));

			if (resultados.containsKey(metric)) {
				List<String> empresas = resultados.get(metric);
				empresas.add(sEmpresa);
				resultados.put(metric, empresas);
			} else {
				List<String> empresas = new ArrayList<String>();
				empresas.add(sEmpresa);
				resultados.put(metric, empresas);
			}

		}
		// cerramos el scanner
		scanner.close();
		// Cerramos la tabla
		tabla.close();

		return resultados;
	}

	/**
	 * Función para entrada de datos del usuario
	 * 
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 */
	private static Date getDate() throws IOException, ParseException {

		InputStreamReader istream = new InputStreamReader(System.in);
		BufferedReader bufRead = new BufferedReader(istream);

		System.out.println("Teclee fecha dd/MM/yyyy");
		dateInString = bufRead.readLine();
		Date date = formatter.parse(dateInString);

		return date;
	}

	public static boolean printProbability(EstimateMarketDrop model,
			Date date) throws IOException {

		Hashtable<Float, List<String>> metricList = model.getMetric(date);
		// imprime los resultados, si los hay
		if (!metricList.isEmpty()) {

			List<Float> claves = Collections.list(metricList.keys());
			Collections.sort(claves);
			float maxProb = Collections.max(claves);

			List<String> empresas = metricList.get(maxProb);
			if (empresas.size() > 1) {
				System.out.println("\nLa probabilidad maxima es " + maxProb
						+ " para las empresas:\n");
				for (String empresa : empresas) {
					System.out.println(empresa);
				}
				System.out.println("\nen el día " + dateInString);
			} else {
				System.out.println("\nLa probabilidad maxima es " + maxProb
						+ " para la empresa " + empresas.get(0) + " en el día "
						+ dateInString);
			}
		} else {
			System.out.println("No existen datos para la fecha introducida "
					+ dateInString);
		}

		return false;
	}

	public static void main(String[] args) throws Exception {

		EstimateMarketDrop model = new EstimateMarketDrop();

		// Fecha de entrada por teclado
		Date date = EstimateMarketDrop.getDate();

		// Buscamos en nuestra tabla la empresa con mayor probabilidad de
		// desplome para la fecha introducida.
		if (EstimateMarketDrop.printProbability(model, date)) {
			return;
		}
	}

}
