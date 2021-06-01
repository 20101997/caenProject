package com.grupposts.trasporti.features.rfid;

import com.caen.RFIDLibrary.CAENRFIDException;
import com.caen.RFIDLibrary.CAENRFIDLogicalSource;
import com.caen.RFIDLibrary.CAENRFIDReader;
import com.caen.RFIDLibrary.CAENRFIDTag;
import com.grupposts.domain.models.Temperature;
import com.grupposts.trasporti.features.rfid.models.Conversion;
import com.grupposts.trasporti.features.rfid.models.RFIDTag;


import java.util.ArrayList;
import java.util.List;

public class ReadTemperature {


    private int WORD = 2; //2 bytes
    private CAENRFIDLogicalSource mSource;
    private CAENRFIDTag mTag;
    private CAENRFIDReader mReader;
    private String mTagHex;



    public String getmTagHex() {
        return mTagHex;
    }

    public ReadTemperature(CAENRFIDReader mReader, String mTagHex) {
        this.mReader = mReader;
        this.mTagHex = mTagHex;
    }


    public Object getTemperatureFromTag(Object... objects) {
        byte[] id = (byte[])objects[0];

        ArrayList<byte[]> tmps = new ArrayList<>();

        try {
            mSource = mReader.GetSource("Source_0");
            mTag = new CAENRFIDTag(id, (short) id.length, mSource, "Ant0");

            short addrSamplesNumber = Short.parseShort((String)objects[1], 16);//2
            short firstSampleValue = Short.parseShort((String)objects[2], 16);//2

            boolean data_loss = false;

            tmps.add( RFIDTag.ReadWithRetry(mTag, (short) 3, (short) ((addrSamplesNumber * WORD) ), (short) (WORD)));

            int samplesNumberValue =0;
            if (tmps.get(0) != null) {
                samplesNumberValue = Short.parseShort(RFIDTag.toHexString(tmps.get(0)), 16);
            }

            for(int i =0; i<samplesNumberValue*3;i++){
                tmps.add( RFIDTag.ReadWithRetry(mTag, (short) 3, (short) (((firstSampleValue + i) * WORD) ), (short) (WORD)));


            }
            for (int i = 0; i < tmps.size(); i++) {
                if (tmps.get(i) == null) {
                    data_loss = true;
                }
            }

            if (data_loss) {
                 throw new Exception("data lost, put the skid near the tag");
            }

        } catch (CAENRFIDException e) {
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }

        //part2
        boolean thereAreNulls =false;

        ArrayList<String> registersWeNeed = new ArrayList<>();
        for (int i = 0; i < tmps.size(); i++) {
            if(tmps.get(i) == null){
                // if some register data is missing
               // registersWeNeed.add("???");
                try {
                    throw new Exception("missing data put the skid near the tag");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {

                //  mValue[i].setText(RFIDTag.toHexString(tmp.get(i)));
                registersWeNeed.add(RFIDTag.toHexString(tmps.get(i)));

            }
        }
        List<Temperature> temperatureList = new ArrayList<Temperature>();
        if(!thereAreNulls){
            for(int i =1; i<registersWeNeed.size()-2; i+=3){

                temperatureList.add(new Temperature(Conversion.hexToTime(registersWeNeed.get(i+2) + registersWeNeed.get(i+1)),
                        Conversion.ConvertHexToTemperature(registersWeNeed.get(i))));

            }
           // t = map.toString();
        }
        return temperatureList;







        // partie thenya

    }
}
