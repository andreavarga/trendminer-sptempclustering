/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package trendminer.sptempclustering;

import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TargetStringToFeatures;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.types.Alphabet;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.util.CommandOption;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import org.joda.time.DateTime;
import org.joda.time.Days;

/**
 *
 * @author andreavarga
 */
public class ImportTrendMinerData {    
    public static DecimalFormat decimalFormat = new DecimalFormat("##");
    
    public static Alphabet alphCity = new Alphabet();
    public static Alphabet alphCountry = new Alphabet();
    
    public static int iTotalRBF = -1;
    //temporal info
    public static List<Integer> lstRBF_Centers = new ArrayList<Integer>();
    public static List<String> lstRBF_Centers_FeatureName = new ArrayList<String>();
    //spatial info
    public static HashMap<Integer, String> hmapCityLongitude = new HashMap<Integer, String>();
    public static HashMap<Integer, String> hmapCityLatitude = new HashMap<Integer, String>();
    public static HashMap<Integer, String> hmapCityCountry = new HashMap<Integer, String>();
    public static HashMap<Integer, String> hmapCityNames = new HashMap<Integer, String>();
    
    //for creating mallet instances
    static Pattern tokenPattern = Pattern.compile("[\\p{L}\\p{N}\\p{Punct}\\S]+");    

    static CommandOption.Double sigma =
            new CommandOption.Double(ImportTrendMinerData.class,
            "sigma", "",
            false,
            50,
            "is the sigma in temporal RBF",
            null);
    static CommandOption.Double sigma_GEO =
            new CommandOption.Double(ImportTrendMinerData.class,
            "sigma_GEO", "",
            false,
            2.5,
            "is the sigma in regional RBF",
            null);
    static CommandOption.Integer startMonth =
            new CommandOption.Integer(ImportTrendMinerData.class,
            "startMonth", "",
            true,
            02,
            "the 1st month from the dataset",
            null);
    static CommandOption.Integer endMonth =
            new CommandOption.Integer(ImportTrendMinerData.class,
            "endMonth", "",
            true,
            12,
            "the last month from the dataset",
            null);

    static CommandOption.Integer startYear =
            new CommandOption.Integer(ImportTrendMinerData.class,
            "startYear", "",
            true,
            2012,
            "the 1st year from the dataset",
            null);
    static CommandOption.Integer endYear =
            new CommandOption.Integer(ImportTrendMinerData.class,
            "endYear", "",
            true,
            2013,
            "the last year from the dataset",
            null);
    static CommandOption.Boolean useCityFeatures =
            new CommandOption.Boolean(ImportTrendMinerData.class,
            "useCityFeatures", "",
            false,
            false,
            "adding city features",
            null);

    static CommandOption.Boolean useCountryFeatures =
            new CommandOption.Boolean(ImportTrendMinerData.class,
            "useCountryFeatures", "",
            false,
            false,
            "end Year",
            null);

    static CommandOption.Boolean useMonthlyIndicatorFeatures =
            new CommandOption.Boolean(ImportTrendMinerData.class,
            "useMonthlyIndicatorFeatures", "",
            false,
            false,
            "these features represent each month using a boolean indicator feature",
            null);
    static CommandOption.String mainDir =
            new CommandOption.String(ImportTrendMinerData.class,
            "mainDir", "",
            true,
            "/Users/andreavarga/NetBeansProjects/trendminer-sptempclustering_dist/",
            "path to the folder containing the data",
            null);
    
    static CommandOption.Boolean geokernel =
            new CommandOption.Boolean(ImportTrendMinerData.class,
            "geokernel", "",
            false,
            false,
            "add spatial features",
            null);
    public static Date datetimeStart;
    public static Calendar calStart;
    public static Date datetimeEnd;
    public static Calendar calEnd;

    public static List<Double> computeRBF(int iDateOffSet) {
        List<Double> lstRBFValues = new ArrayList<Double>();

        Double dVal = -1.0;
        long lSquare = -1;
        Double dSigma_square = sigma.value * sigma.value;
        for (int i = 0; i < lstRBF_Centers.size(); i++) {
            lSquare = (lstRBF_Centers.get(i) - iDateOffSet) * (lstRBF_Centers.get(i) - iDateOffSet);
            dVal = Math.exp((-1.0 * lSquare) / (2.0 * dSigma_square));
            lstRBFValues.add(dVal);
        }

        return lstRBFValues;
    }

    public static List<Double> computeGeoRBF(int iCity) {
        List<Double> lstRBFValues = new ArrayList<Double>();

        Double dVal = -1.0;

        Double dLongCity2 = Double.parseDouble(hmapCityLongitude.get(iCity));
        Double dLatCity2 = Double.parseDouble(hmapCityLatitude.get(iCity));

        Double dDist = 0.0;
        Double dSigma_square = sigma_GEO.value * sigma_GEO.value;

        for (int i = 0; i < hmapCityCountry.keySet().size(); i++) {
            Double dLongCity1 = Double.parseDouble(hmapCityLongitude.get(i));
            Double dLatCity1 = Double.parseDouble(hmapCityLatitude.get(i));

            dDist = Math.pow(dLongCity2 - dLongCity1, 2.0)
                    + Math.pow(dLatCity2 - dLatCity1, 2.0);

            dVal = Math.exp((-1.0 * dDist) / (2.0 * dSigma_square));
            lstRBFValues.add(dVal);
        }

        return lstRBFValues;
    }

    public static String compute_Month_Indicators(int iMonthValue, int iDateValue) {

        String sRBF = "";
        String sFeatureValue = "dist_to_" + iMonthValue + "_" + iDateValue;
        String sVal = "";

        for (int h = 0; h < lstRBF_Centers.size(); h++) {
            sVal = lstRBF_Centers_FeatureName.get(h).toString() + "=";
            sVal = sVal.replaceAll("dist_to_", "month_");
            if (lstRBF_Centers_FeatureName.get(h).toString().compareTo(sFeatureValue) == 0) {
                sRBF += sVal + "1.0 ";
            } else {
                sRBF += sVal + "0.0 ";
            }
        }
        sRBF += "\n";

        return sRBF;
    }

    public static String computeRBFkernels(int iDateIdx) {
        List<Double> lst_RBFDiff = null;
        lst_RBFDiff = computeRBF(iDateIdx);

        String sRBF = "";

        for (int h = 0; h < lstRBF_Centers.size(); h++) {
            sRBF += lstRBF_Centers_FeatureName.get(h).toString() + "="
                    + lst_RBFDiff.get(h).toString() + " ";
        }
        sRBF += "\n";

        return sRBF;
    }

    public static String computeGeoRBFkernels(int iCity) {
        List<Double> lst_RBFDiff = null;
        lst_RBFDiff = computeGeoRBF(iCity);

        String sRBF = "";
        for (int h = 0; h < hmapCityCountry.keySet().size(); h++) {
            sRBF += "georbf" + hmapCityNames.get(h) + "="
                    + lst_RBFDiff.get(h).toString() + " ";
        }
        return sRBF;
    }

    public static String addCityFeatures(int iUserIdx) {

        String sUserFeatures = "";
        for (int h = 0; h < alphCity.size(); h++) {
            if (h == iUserIdx) {
                sUserFeatures += alphCity.lookupObject(h) + "=1" + " ";
            } else {
                sUserFeatures += alphCity.lookupObject(h) + "=0" + " ";
            }
        }
        return sUserFeatures;
    }

    public static String addCountryFeatures(String sCountry) {

        String sCountryFeatures = "";
        for (int h = 0; h < alphCountry.size(); h++) {
            if (alphCountry.lookupObject(h).toString().contains(sCountry)) {
                sCountryFeatures += alphCountry.lookupObject(h) + "=1" + " ";
            } else {
                sCountryFeatures += alphCountry.lookupObject(h) + "=0" + " ";
            }
        }
        return sCountryFeatures;
    }

    public static void main(String[] args) {
        try {
            CommandOption.setSummary(ImportTrendMinerData.class,
                    "Inporting Trendminer data into Mallet - developed between Dec 2013 - April 2014");
            CommandOption.process(ImportTrendMinerData.class, args);
            CommandOption.printOptionValues(ImportTrendMinerData.class);

            Pipe instancePipe =
                    new SerialPipes(new Pipe[]{
                        (Pipe) new TargetStringToFeatures(),
                        (Pipe) new CharSequence2TokenSequence(tokenPattern),
                        (Pipe) new TokenSequence2FeatureSequence()
                    });

            iTotalRBF = (endYear.value - startYear.value + 1) * 12;
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            datetimeStart = dateFormat.parse(startYear.value + "-01-01");//1st Jan

            int iStartYear = startYear.value;

            DateTime dtStart_Joda = new DateTime(
                    iStartYear, startMonth.value, 1, 0, 0, 0, 0);

            DateTime dtEnd_Joda = new DateTime(startYear.value, 1, 1, 0, 0, 0, 0);
            calStart = Calendar.getInstance();
            calStart.setTime(datetimeStart);


            int iYearDiffValue = endYear.value - startYear.value + 1;
            for (int k = 0; k < (endYear.value - startYear.value + 1); k++) {
                for (int iMonth = 1; iMonth <= 12; iMonth++) {
                    if (k == 0) {
                        if (iMonth < startMonth.value) {
                            continue;
                        }
                    }
                    if (k == (iYearDiffValue - 1)) {
                        if (iMonth > endMonth.value) {
                            continue;
                        }
                    }
                    datetimeEnd = dateFormat.parse((startYear.value + k)
                            + "-"
                            + decimalFormat.format(iMonth * 1.0)
                            + //                            "-01" +
                            "-01");//1st Jan
                    calEnd = Calendar.getInstance();
                    calEnd.setTime(datetimeEnd);
                    dtEnd_Joda = new DateTime((startYear.value + k), iMonth, 1, 0, 0, 0, 0);
                    Days d = Days.daysBetween(dtStart_Joda, dtEnd_Joda);
                    lstRBF_Centers.add(d.getDays());
                    lstRBF_Centers_FeatureName.add("dist_to_" + iMonth + "_" + (startYear.value + k));
                }
            }

            System.out.println();
            String sMainDir = mainDir.value;

            /// Reading --- DICTIONARY ---
            String sDictionary = sMainDir + "dictionary";
            System.out.println("Reading " + sDictionary);
            BufferedReader fDictionary = new BufferedReader(new FileReader(new File(sDictionary)));

            Alphabet alphDictionary = new Alphabet();
            String sLine = "";
            String[] arrWords = null;
            while ((sLine = fDictionary.readLine()) != null) {
                arrWords = sLine.split(" ");
                alphDictionary.lookupIndex(arrWords[1], true);
            }

            fDictionary.close();
            System.out.println("Loaded");

            if (useCountryFeatures.value) {
                /// --- City country mapping ---
                String sUser_Country = sMainDir + "GEO";
                BufferedReader fUsers_Country = new BufferedReader(new FileReader(new File(sUser_Country)));

                String sCountry = "";
                while ((sLine = fUsers_Country.readLine()) != null) {
                    arrWords = sLine.split(" ");
                    int iUserIdIdx = Integer.parseInt(arrWords[0]);
                    //0 Bregenz 47.5167 9.7667 Austria
                    hmapCityLongitude.put(iUserIdIdx, arrWords[3]);
                    hmapCityLatitude.put(iUserIdIdx, arrWords[2]);

                    sCountry = "." + arrWords[4];
                    alphCountry.lookupIndex(sCountry, true);
                    hmapCityCountry.put(iUserIdIdx, sCountry);
                    hmapCityNames.put(iUserIdIdx, arrWords[1]);
                }
                System.out.println("alphCountry.size():" + alphCountry.size());
                fUsers_Country.close();
            }


            ///Reading --- Cities ---
            String sCities = sMainDir + "cities";
            System.out.println("Reading " + sCities);
            BufferedReader fCities = new BufferedReader(new FileReader(new File(sCities)));
            String sCountry = "";
            while ((sLine = fCities.readLine()) != null) {
                arrWords = sLine.split(" ");
                alphCity.lookupIndex(arrWords[1], true);
            }

            System.out.println("Loaded");
            System.out.println("alphCity.size():" + alphCity.size());
            fCities.close();

            /// Reading --- DATES ---
            String sDates = sMainDir + "dates";
            System.out.println("Reading " + sDates);
            BufferedReader frW_Dates = new BufferedReader(new FileReader(new File(sDates)));

            Alphabet alphDates = new Alphabet();

            HashMap<Integer, String> hmapDateIdx_DateName = new HashMap<Integer, String>();
            while ((sLine = frW_Dates.readLine()) != null) {
                arrWords = sLine.split(" ");
                alphDates.lookupIndex(arrWords[1], true);
                hmapDateIdx_DateName.put(Integer.parseInt(arrWords[0]), arrWords[1]);
            }
            frW_Dates.close();
            System.out.println("Loaded");

            String sDMR_FileSuffix = "";

            if (useMonthlyIndicatorFeatures.value){
                sDMR_FileSuffix = "Mid";
            }else{
                sDMR_FileSuffix = "TimeRBF";
            }
            if (geokernel.value) {
                sDMR_FileSuffix += 
                        ".GeoRBF";
            }

            String sSuffix = "";
            if (useCityFeatures.value) {
                sSuffix += ".City";
            }

            if (useCountryFeatures.value) {
                sSuffix += ".Country";
            }

            BufferedReader fsora_vs = new BufferedReader(new FileReader(new File(sMainDir + "sora_vs")));
            System.out.println("Reading sora");

            String sUserId = "";
            String sCurrentUserId = "";
            int iDicIdx = -1;
            int iRepetitionIdx = -1;
            int iDateIdx = -1;
            int iDateValue = -1;

            int iMonthValue = -1;
            int iCityIdx = -1;

            int iLineNr = -1;
            
            InstanceList instancesDMR = new InstanceList(instancePipe);
            ArrayList<Instance> instanceBuffer = new ArrayList<Instance>();
            String sWordsLine ="";
            String sFeaturesLine ="";

            //sora_vs is in the format: 
            //date_id[SPACE]user_id[SPACE]token_id[TAB]frequency (The tokens for each user and day)
            while ((sLine = fsora_vs.readLine()) != null) {
                sLine = sLine.replaceAll(" \t", "\t");
                arrWords = sLine.split(" ");
                sUserId = arrWords[1];

                iCityIdx = Integer.parseInt(sUserId);

                iDicIdx = Integer.parseInt(arrWords[2].split("\t")[0]);
                System.out.println(sLine);
                if (arrWords[2].split("\t").length == 1) {
                    iRepetitionIdx = Integer.parseInt(arrWords[3].trim());
                } else {
                    iRepetitionIdx = Integer.parseInt(arrWords[2].split("\t")[1].trim());
                }
                iDateIdx = Integer.parseInt(arrWords[0]);

                String sDateTime = hmapDateIdx_DateName.get(iDateIdx);

                iDateValue =
                        Integer.parseInt(sDateTime.split("-")[0]);
                iMonthValue = Integer.parseInt(sDateTime.split("-")[1]);

                if ((iDateValue >= startYear.value) && (iDateValue <= endYear.value)) {} 
                else continue;
                

                sCountry = alphCity.lookupObject(iCityIdx).toString();
                if (sCountry.contains(".")) {
                    sCountry = sCountry.substring(sCountry.lastIndexOf("."));
                }
                if (sCurrentUserId.length() == 0) {
                    sCurrentUserId = sUserId;
                    if (useCityFeatures.value) {
                        sFeaturesLine += addCityFeatures(iCityIdx);
                    }

                    if (useCountryFeatures.value) {
                        sCountry = hmapCityCountry.get(iCityIdx);
                        if (sCountry.length() > 0) {
                            sFeaturesLine += addCountryFeatures(sCountry);
                        }
                    }

                    if (useMonthlyIndicatorFeatures.value) {
                        sFeaturesLine += compute_Month_Indicators(iMonthValue, iDateValue);
                    } else {
                        if (geokernel.value) {
                            sFeaturesLine += computeGeoRBFkernels(iCityIdx);
                        }
                        sFeaturesLine += computeRBFkernels(iDateIdx);
                    }
                }

                //same user
                if (sUserId.compareTo(sCurrentUserId) == 0) {
                    for (int k = 0; k < iRepetitionIdx; k++) {
                        sWordsLine += alphDictionary.lookupObject(iDicIdx).toString() + " ";
                    }
                } else {
                    try {
                        iLineNr++;
                        instanceBuffer.add(new Instance(sWordsLine, sFeaturesLine, String.valueOf(iLineNr), null));
                        sWordsLine = "";
                        sFeaturesLine = "";
                        if (useCityFeatures.value) {
                            sFeaturesLine += addCityFeatures(iCityIdx);
                        }

                        if (useCountryFeatures.value) {
                            sCountry = hmapCityCountry.get(iCityIdx);
                            if (sCountry.length() > 0) {
                                sFeaturesLine += addCountryFeatures(sCountry);
                            }
                        }
                        if (useMonthlyIndicatorFeatures.value) {
                            sFeaturesLine += compute_Month_Indicators(iMonthValue, iDateValue);
                        } else {
                            if (geokernel.value) {
                                sFeaturesLine += computeGeoRBFkernels(iCityIdx);
                            }
                            sFeaturesLine += computeRBFkernels(iDateIdx);
                        }


                        for (int k = 0; k < iRepetitionIdx; k++) {
                            sWordsLine += alphDictionary.lookupObject(iDicIdx).toString() + " ";
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    sCurrentUserId = sUserId;
                }
            }

            fsora_vs.close();
            
            iLineNr++;
            instanceBuffer.add(new Instance(sWordsLine, sFeaturesLine, String.valueOf(iLineNr), null));
            
            instancesDMR.addThruPipe(instanceBuffer.iterator());

            System.out.println("instances.size():"+ instancesDMR.size());
            
            dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
            datetimeStart = new Date();
            calStart = Calendar.getInstance();
            calStart.setTime(datetimeStart);
            
            File fDMR_MalletInstances = new File(sMainDir
                    + "dmr." + new File(sMainDir).getName()+"-"+
                    sDMR_FileSuffix + sSuffix+"-"+
                    dateFormat.format(datetimeStart)
                    + ".mallet");
            
            ObjectOutputStream oos =
                    new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(fDMR_MalletInstances)));
            oos.writeObject(instancesDMR);
            oos.close();
            

            System.out.println("Finished");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
