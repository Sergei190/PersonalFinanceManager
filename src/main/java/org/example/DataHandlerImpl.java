package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class DataHandlerImpl implements DataHandler {

    // По условиям задания загрузка/сохранения всегда осуществляются
    private static boolean LOAD_DATA = true;
    private static boolean SAVE_DATA = true;

    // Запрет на загрузку/сохранение данных, в интерфейсе не описан
    // For using with Unit tests ONLY
    public static void setLoadAndSaveDataDisabled() {
        LOAD_DATA = false;
        SAVE_DATA = false;
    }

    private static DataHandlerImpl INSTANCE;

    // Внешний "конструктор"
    public static DataHandlerImpl getInstance() throws IOException, ClassNotFoundException {
        if (INSTANCE == null) {
            INSTANCE = new DataHandlerImpl();
        }
        return INSTANCE;
    }

    // Чтобы знать, за какую дату возвращать отчет
    private String lastSaleDate;

    //  Справочник соответствия категорий товарам
    private final Map<String, String> goodsByCategory;
    //  Продажи по категориям товаров общие, по годам, месяцам и дням
    private final AllSalesData allSalesData;

    // Конструктор и инициализация данных
    private DataHandlerImpl() throws IOException, ClassNotFoundException {
        // Загрузка справочников товаров и категорий
        this.goodsByCategory = DataManagement.loadCategories();
        // Загрузка/Инициализация структур данных
        if (DataManagement.existSavedData() && LOAD_DATA) {
            this.allSalesData = (AllSalesData) DataManagement.loadAllDataFromBinFile();
        } else {
            this.allSalesData = new AllSalesData();
            allSalesData.maxCategory = new HashMap<>();
            allSalesData.yearlySales = new HashMap<>();
            allSalesData.monthlySales = new HashMap<>();
            allSalesData.dailySales = new HashMap<>();
        }
    }

    @Override
    public void addSale(String newSaleForAdd) throws IOException {
        // Распихиваем строку JSON в JAVA объект
        CategorySalesData categorySalesData = salesRecordConversion(newSaleForAdd);

        var category = categorySalesData.category;
        var date = categorySalesData.date;
        var saleSum = categorySalesData.sum;
        // Сохраним дату текущей транзакции в поле класса
        lastSaleDate = date;
        // Плюсуем общие продажи (без учета даты)
        int currentSales = allSalesData.maxCategory.getOrDefault(category, 0);
        allSalesData.maxCategory.put(category, currentSales + categorySalesData.sum);
        Map<String, Integer> localMap;
        // Годовые продажи
        var year = date.substring(0, 4);
        localMap = allSalesData.yearlySales.getOrDefault(year, new HashMap<>());

        currentSales = localMap.getOrDefault(category, 0);
        localMap.put(category, currentSales + saleSum);

        allSalesData.yearlySales.put(year, localMap);
        // Месячные продажи
        var month = date.substring(0, 7);
        localMap = allSalesData.monthlySales.getOrDefault(month, new HashMap<>());

        currentSales = localMap.getOrDefault(category, 0);
        localMap.put(category, currentSales + saleSum);

        allSalesData.monthlySales.put(month, localMap);
        // Дневные продажи
        localMap = allSalesData.dailySales.getOrDefault(date, new HashMap<>());

        currentSales = localMap.getOrDefault(category, 0);
        localMap.put(category, currentSales + saleSum);

        allSalesData.dailySales.put(date, localMap);

        // Сохраняем данные
        if (SAVE_DATA) {
            DataManagement.saveAllDataToBinFile(allSalesData);
        }
    }

    private CategorySalesData salesRecordConversion(String saleRecordJSON) {
        var gson = new Gson();
        var salesRecord = gson.fromJson(saleRecordJSON, SalesRecordFromJSON.class);

        var saleCategory = goodsByCategory.get(salesRecord.title);
        if (saleCategory == null) {saleCategory = "другое";}

        var date = salesRecord.date;
        var saleSum = salesRecord.sum;
        return new CategorySalesData(saleCategory, date, saleSum);
    }

    @Override
    public String generateAnalysisResults() {

        String key = getEntryWithMaximumSales(allSalesData.maxCategory);
        var maxInCategory = new SaleDataForOutput(key, allSalesData.maxCategory.get(key));
        var result = new AllAnalysisResults();
        result.maxCategory = maxInCategory;

        Map<String, Integer> localMap;
        // Годовые продажи
        var year = lastSaleDate.substring(0, 4);

        localMap = allSalesData.yearlySales.get(year);
        key = getEntryWithMaximumSales(localMap);

        result.maxYearCategory = new SaleDataForOutput(key, localMap.get(key));
        // Месячные продажи
        var month = lastSaleDate.substring(0, 7);

        localMap = allSalesData.monthlySales.get(month);
        key = getEntryWithMaximumSales(localMap);

        result.maxMonthCategory = new SaleDataForOutput(key, localMap.get(key));
        // Дневные продажи
        var date = lastSaleDate;

        localMap = allSalesData.dailySales.get(date);
        key = getEntryWithMaximumSales(localMap);

        result.maxDayCategory = new SaleDataForOutput(key, localMap.get(key));
        // Запихнули в JSON и отдали
        var gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(result);
    }

    private String getEntryWithMaximumSales(Map<String, Integer> salesMap) {
        int maxSale = -1;
        String key = null;
        for (Map.Entry<String, Integer> entryMap : salesMap.entrySet()) {
            if (entryMap.getValue() > maxSale) {
                key = entryMap.getKey();
                maxSale = entryMap.getValue();
            }
        }
        return key;
    }

    private class SalesRecordFromJSON {
        String title;
        String date;
        int sum;
    }

    private class CategorySalesData {
        String category;
        String date;
        int sum;

        public CategorySalesData(String category, String date, int sum) {
            this.category = category;
            this.date = date;
            this.sum = sum;
        }
    }

    private static class AllSalesData implements Serializable {
        Map<String, Integer> maxCategory;
        Map<String, Map<String, Integer>> yearlySales;
        Map<String, Map<String, Integer>> monthlySales;
        Map<String, Map<String, Integer>> dailySales;
    }

    private class AllAnalysisResults {
        SaleDataForOutput maxCategory;
        SaleDataForOutput maxYearCategory;
        SaleDataForOutput maxMonthCategory;
        SaleDataForOutput maxDayCategory;
    }

    private class SaleDataForOutput {
        String category;
        int sum;

        public SaleDataForOutput(String category, int sum) {
            this.category = category;
            this.sum = sum;
        }
    }
}
