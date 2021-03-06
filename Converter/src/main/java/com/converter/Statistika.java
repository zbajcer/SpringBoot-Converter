package com.converter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import com.converter.jpa.DailyStats;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
@Component
public class Statistika {

	ConverterDAOImpl impl = new ConverterDAOImpl();
	
	public void updateCounter(String polaznaValuta) throws ParseException, SQLException {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
		String time = sdf.format(date);
		SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy");
		java.util.Date dateStr = format.parse(time);
		java.sql.Date dateDB = new java.sql.Date(dateStr.getTime());
		Connection conne = impl.connect();
		String sql = "INSERT INTO DailyStats (Valuta,Datum,Counter) VALUES(?,?,?)" + // primary key(Valuta,Datum)																				
				"ON DUPLICATE KEY UPDATE Counter = Counter + 1;";
		try {
			PreparedStatement ps = conne.prepareStatement(sql);
			ps.setString(1, polaznaValuta);
			ps.setDate(2, dateDB);
			ps.setString(3, "1");
			ps.execute();
			conne.close();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if (conne != null) {
				conne.close();
			}
		}
	}

	public JSONArray getMostCommonOverall() throws SQLException {
		String sql = "SELECT Valuta, SUM(counter) AS Ukupno FROM DailyStats GROUP BY Valuta ORDER BY Ukupno DESC LIMIT 1";
		String result = "";
		JSONObject output = new JSONObject();
		JSONArray array = new JSONArray();
		Connection conne = impl.connect();
		try {
			ResultSet rs = null;
			rs = conne.createStatement().executeQuery(sql);
			while (rs.next()) {
				result = rs.getString(1);
			}
			output.put("value", result);
			array.put(output);
			conne.close();
		} catch (SQLException | JSONException e1) {
			e1.printStackTrace();
		} finally {
			if (conne != null) {
				conne.close();
			}
		}
		return array;
	}

	public JSONArray getMostComonInterval(int interval) throws SQLException {
		String sql = "SELECT Valuta, SUM(counter) AS total FROM DailyStats WHERE Datum >= DATE(NOW()) - INTERVAL ? DAY GROUP BY Valuta ORDER BY total DESC LIMIT 1";
		ResultSet rs = null;
		String result = "";
		JSONObject output = new JSONObject();
		JSONArray array = new JSONArray();
		Connection conne = impl.connect();
		try {
			PreparedStatement ps = conne.prepareStatement(sql);
			ps.setInt(1, interval);
			rs = ps.executeQuery();
			while (rs.next()) {
				result = rs.getString(1);
			}
			output.put("value", result);
			array.put(output);
			conne.close();
		} catch (SQLException | JSONException e) {
			e.printStackTrace();
		} finally {
			if (conne != null) {
				conne.close();
			}
		}
		return array;
	}

	public JSONArray getIntervalStats(int interval, String valuta) throws SQLException, JsonProcessingException {
		LocalDate date = LocalDate.now().minusDays(interval);
		EntityManagerFactory emf = Persistence.createEntityManagerFactory("converterPersistence");
	    EntityManager manager = emf.createEntityManager();
		@SuppressWarnings("unchecked")
		List<Object[]> objects = manager.createQuery(
				"SELECT  DATE_FORMAT(Datum, '%d-%m-%Y') AS Datum, Counter from DailyStats where Valuta = :valuta and Datum <= DATE(NOW()) AND Datum >= :date")
				.setParameter("valuta", valuta)
				.setParameter("date", date)
				.getResultList();
        List<DailyStats> stats = new ArrayList<>(objects.size());
        for(Object[] obj: objects) {
        	stats.add(new DailyStats((String) obj[0], (Integer) obj[1]));
        }
        String jsonArray = null;
        ObjectMapper mapper = new ObjectMapper();
        jsonArray = mapper.writeValueAsString(stats);
        manager.close();
        emf.close();
        JSONArray array = new JSONArray(jsonArray);
		return array;
	}
	
	public JSONArray getConverterStatistics(int mostCommonInterval, int currencyInterval, String currency) {
		JSONArray jsonArray = new JSONArray();
		JSONObject obj = new JSONObject();
		try {
			obj.put("mostCommonOverall", getMostCommonOverall());
			obj.put("mostCommonInterval",getMostComonInterval(mostCommonInterval));
			obj.put("currencyInterval",getIntervalStats(currencyInterval, currency));
			jsonArray.put(obj);
		} catch (SQLException | JsonProcessingException e) {
			e.printStackTrace();
		}
		return jsonArray;
	}
}