/*
 * Based on Ichi Hirota's dual-fisheye plug-in for the THETA V.
 * Modified to use Shutter speed instead of exposure compensation
 * Added openCV support
 *
 * TODO ideas
 * - split in two seperate pieces with unstiched version to get seperate crc -> weird crash -> maybe split stiched pic in two?
 * - export default python script to recreate hdri offline?
 * - add dng  to output -> support adobe dng sdk
 * - support opencv 4
 * - fix black hole sun
 * - support Z1
 * - support tonemapped jpg in theta default app -> no idea why it doesn't work, maybe something with adding right exif data but maybe not.
 *
 * TODO v2
 * - add web interface
 * - turn sound on/off
 * - turn iso looping on/off
 * - exr half/full float on/off
 * - download exr
 * - name session
 * - viewer
 * - show status
 * - stops step setting?
 * - number of pics?
 * - dng and exr support
 */



//package com.theta360.pluginapplication;
package com.kasper.hdr2exr;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.support.media.ExifInterface;
import android.view.ViewDebug;

import org.opencv.android.OpenCVLoader;

import static org.opencv.core.CvType.typeToString;
import static org.opencv.imgcodecs.Imgcodecs.imread;
import static org.opencv.imgcodecs.Imgcodecs.imwrite;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.String;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.opencv.core.MatOfInt;
import org.opencv.core.Scalar;



import com.theta360.pluginlibrary.activity.PluginActivity;
import com.theta360.pluginlibrary.callback.KeyCallback;
import com.theta360.pluginlibrary.receiver.KeyReceiver;
import com.theta360.pluginlibrary.values.LedColor;
import com.theta360.pluginlibrary.values.LedTarget;

import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;
import java.text.DecimalFormat;

import java.nio.channels.FileChannel;


import static java.lang.Thread.sleep;
import java.util.ArrayList;
import java.util.function.DoubleToIntFunction;


public class MainActivity extends PluginActivity implements SurfaceHolder.Callback {

    //#################################################################################################
    private static final int numberOfPictures = 11;    // number of pictures for the bracket          #
    private static final int number_of_noise_pics = 3; // number of pictures take for noise reduction #
    //#################################################################################################

    Double stop_jumps = 2.5;      // stops jump between each bracket has become dynamic            #

    private Camera mCamera = null;
    private Context mcontext;
    private int bcnt = 0; //bracketing count

    Double[][] bracket_array = new Double[numberOfPictures][5];
    Mat times = new Mat(numberOfPictures,1,CvType.CV_32F);

    int current_count = 0;
    int noise_count = number_of_noise_pics;

    int cols = 5376;
    int rows = 2688;

    ArrayList<String> filename_array = new ArrayList<String>();
    ArrayList<String> images_filename_array = new ArrayList<String>();


    String auto_pic;
    byte[] saved_white_data;
    String white_picture ="";
    String session_name ="";
    List<Mat> images = new ArrayList<Mat>(numberOfPictures);
    Mat average_pic = new Mat();
    Mat temp_pic = new Mat();
    Mat average_pic_jpg = new Mat();

    // Set exr file to half float --> smaller files
    private MatOfInt compressParams = new MatOfInt(org.opencv.imgcodecs.Imgcodecs.CV_IMWRITE_EXR_TYPE, org.opencv.imgcodecs.Imgcodecs.IMWRITE_EXR_TYPE_HALF);

    // true will start with bracket
    private boolean m_is_bracket = true;
    private boolean m_is_auto_pic = true;

    Double shutter_table[][] =
            {
                    {0.0,  1/25000.0}, {1.0, 1/20000.0}, {2.0,  1/16000.0}, {3.0,  1/12500.0},
                    {4.0,  1/10000.0}, {5.0, 1/8000.0},  {6.0,  1/6400.0},  {7.0,  1/5000.0},
                    {8.0,  1/4000.0},  {9.0, 1/3200.0},  {10.0,	1/2500.0},  {11.0, 1/2000.0},
                    {12.0, 1/1600.0}, {14.0, 1/1000.0},  {15.0,	1/800.0},   {16.0, 1/640.0},
                    {17.0, 1/500.0},  {18.0, 1/400.0},   {19.0, 1/320.0},   {20.0, 1/250.0},
                    {21.0, 1/200.0},  {22.0, 1/160.0},   {23.0,	1/125.0},   {24.0, 1/100.0},
                    {25.0,	1/80.0}, {26.0,	1/60.0}, {27.0,	1/50.0}, {28.0,	1/40.0},
                    {29.0,	1/30.0}, {30.0,	1/25.0}, {31.0,	1/20.0}, {32.0,	1/15.0},
                    {33.0,	1/13.0}, {34.0,	1/10.0}, {35.0,	1/8.0}, {36.0,	1/6.0},
                    {37.0,	1/5.0}, {38.0,	1/4.0}, {39.0,	1/3.0}, {40.0,	1/2.5},
                    {41.0,	1/2.0}, {42.0,	1/1.6}, {43.0,	1/1.3}, {44.0,	1.0},
                    {45.0,	1.3}, {46.0,	1.6}, {47.0,	2.0}, {48.0,	2.5},
                    {49.0,	3.2}, {50.0,	4.0}, {51.0,	5.0}, {52.0,	6.0},
                    {53.0,	8.0}, {54.0,	10.0}, {55.0,	13.0}, {56.0,	15.0},
                    {57.0,	20.0}, {58.0,	25.0}, {59.0,	30.0}, {59.0,	40.0}, {59.0,	50.0},{60.0,	60.0},
                    {60.0,	80.0},{60.0,	100.0}, {60.0,	120.0},{60.0,	140.0},{60.0,	160.0}, {60.0,	180.0},{60.0,	200.0}
            };

    private static final String TAG = "MainActivity";

    static {
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG,"OpenCV initialize success");
        } else {
            Log.i(TAG, "OpenCV initialize failed");
        }
    }

    private void copyWithChannels(File source, File target, boolean append) {
        //log("Copying files with channels.");
        //ensureTargetDirectoryExists(target.getParentFile());
        FileChannel inChannel = null;
        FileChannel outChannel = null;
        FileInputStream inStream = null;
        FileOutputStream outStream = null;
        try{
            try {
                inStream = new FileInputStream(source);
                inChannel = inStream.getChannel();
                outStream = new  FileOutputStream(target, append);
                outChannel = outStream.getChannel();
                long bytesTransferred = 0;
                //defensive loop - there's usually only a single iteration :
                while(bytesTransferred < inChannel.size()){
                    bytesTransferred += inChannel.transferTo(0, inChannel.size(), outChannel);
                }
            }
            finally {
                //being defensive about closing all channels and streams
                if (inChannel != null) inChannel.close();
                if (outChannel != null) outChannel.close();
                if (inStream != null) inStream.close();
                if (outStream != null) outStream.close();
            }
        }
        catch (FileNotFoundException ex){
            Log.d(TAG,"File not found: " + ex);
        }
        catch (IOException ex){
            Log.d(TAG,"Error"+ex);
        }
    }


    /* Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_main);
        mcontext = this;
        SurfaceView preview = (SurfaceView)findViewById(R.id.preview_id);
        SurfaceHolder holder = preview.getHolder();
        holder.addCallback(this);
        setKeyCallback(new KeyCallback() {
            @Override
            public void onKeyDown(int keyCode, KeyEvent event) {
                if (keyCode == KeyReceiver.KEYCODE_CAMERA) {
                    // If on second run we need to reset everything.
                    notificationLedBlink(LedTarget.LED3, LedColor.GREEN, 300);
                    current_count = 0;
                    m_is_auto_pic = true;
                    times = new Mat(numberOfPictures,1,org.opencv.core.CvType.CV_32F);
                    images = new ArrayList<Mat>(numberOfPictures);
                    //images_before_avg = new ArrayList<Mat>(numberOfPictures * number_of_noise_pics);


                    customShutter();
                }
                else if(keyCode == KeyReceiver.KEYCODE_WLAN_ON_OFF){ // Old code
                    notificationLedBlink(LedTarget.LED3, LedColor.MAGENTA, 2000);

                }
            }

            @Override
            public void onKeyUp(int keyCode, KeyEvent event) {
                /*
                 * You can control the LED of the camera.
                 * It is possible to change the way of lighting, the cycle of blinking, the color of light emission.
                 * Light emitting color can be changed only LED3.
                 */
            }

            @Override
            public void onKeyLongPress(int keyCode, KeyEvent event) {
                notificationError("theta debug: " + Integer.toString(keyCode) + " was pressed too long");
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        if(m_is_bracket){
            notificationLedBlink(LedTarget.LED3, LedColor.MAGENTA, 2000);
            notificationLedHide(LedTarget.LED3);
            notificationLedShow(LedTarget.LED3);
            notificationLed3Show(LedColor.MAGENTA);
        }
        else {
            notificationLedBlink(LedTarget.LED3, LedColor.CYAN, 2000);
        }
    }

    public void onPause() {
        super.onPause();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        Log.i(TAG,"Camera opened");
        //LoadText(R.raw.master_crc_kasper);

        Intent intent = new Intent("com.theta360.plugin.ACTION_MAIN_CAMERA_CLOSE");
        sendBroadcast(intent);
        mCamera = Camera.open();
        try {

            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {

            //e.printStackTrace();
            Log.i(TAG,"Camera opening error.");
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        mCamera.stopPreview();
        Camera.Parameters params = mCamera.getParameters();
        params.set("RIC_SHOOTING_MODE", "RicMonitoring");

        List<Camera.Size> previewSizes = params.getSupportedPreviewSizes();
        Camera.Size size = previewSizes.get(0);
        for(int i = 0; i < previewSizes.size(); i++) {
            size = previewSizes.get(i);
            Log.d(TAG,"preview size = " + size.width + "x" + size.height);
        }
        params.setPreviewSize(size.width, size.height);
        mCamera.setParameters(params);
        mCamera.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

        Log.d(TAG,"camera closed");
        notificationLedHide(LedTarget.LED3);
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
        Intent intent = new Intent("com.theta360.plugin.ACTION_MAIN_CAMERA_OPEN");
        sendBroadcast(intent);
    }

    private void customShutter(){
        Intent intent = new Intent("com.theta360.plugin.ACTION_AUDIO_SH_OPEN");
        sendBroadcast(intent);

        Camera.Parameters params = mCamera.getParameters();
        //Log.d("shooting mode", params.flatten());
        params.set("RIC_SHOOTING_MODE", "RicStillCaptureStd");

        //params.set("RIC_PROC_STITCHING", "RicNonStitching");
        //params.setPictureSize(5792, 2896); // no stiching

        params.setPictureFormat(ImageFormat.JPEG);
        params.set("jpeg-quality",100);
        //params.setPictureSize(5376, 2688); // stiched
        params.setPictureSize(cols, rows);


        // https://api.ricoh/docs/theta-plugin-reference/camera-api/
        //Shutter speed. To convert this value to ordinary 'Shutter Speed';
        // calculate this value's power of 2, then reciprocal. For example,
        // if value is '4', shutter speed is 1/(2^4)=1/16 second.
        //params.set("RIC_EXPOSURE_MODE", "RicManualExposure");

        //params.set("RIC_MANUAL_EXPOSURE_TIME_REAR", -1);
        //params.set("RIC_MANUAL_EXPOSURE_ISO_REAR", -1);


        // So here we take our first picture on full auto settings to get
        // proper lighting settings to use a our middle exposure value
        params.set("RIC_EXPOSURE_MODE", "RicAutoExposureP");

        bcnt = numberOfPictures * number_of_noise_pics;



        mCamera.setParameters(params);
        //params = mCamera.getParameters();

        session_name = getSessionName();

        Log.i(TAG,"Starting new session with name: " + session_name);
        Log.i(TAG,"About to take first auto picture to measure lighting settings.");
        new File(Environment.getExternalStorageDirectory().getPath()+ "/DCIM/100RICOH/"+session_name).mkdir();

        //Log.d("get", params.get("RIC_MANUAL_EXPOSURE_ISO_BACK"));

        //3sec delay timer to run away

        try{
            sleep(5000);
        } catch (InterruptedException e) {
            //e.printStackTrace();
            Log.i(TAG,"Sleep error.");


        }
        intent = new Intent("com.theta360.plugin.ACTION_AUDIO_SHUTTER");
        sendBroadcast(intent);
        mCamera.takePicture(null,null, null, pictureListener);
    }

    private void nextShutter(){
        //restart preview
        Camera.Parameters params = mCamera.getParameters();
        params.set("RIC_SHOOTING_MODE", "RicMonitoring");
        mCamera.setParameters(params);
        mCamera.startPreview();

        //shutter speed based bracket
        if(bcnt > 0) {
            params = mCamera.getParameters();
            params.set("RIC_SHOOTING_MODE", "RicStillCaptureStd");
            //shutterSpeedValue = shutterSpeedValue + shutterSpeedSpacing;
            if (m_is_auto_pic) {
                // So here we take our first picture on full auto settings to get
                // proper lighting settings to use a our middle exposure value
                params.set("RIC_EXPOSURE_MODE", "RicAutoExposureP");
            } else {
                params.set("RIC_EXPOSURE_MODE", "RicManualExposure");
                params.set("RIC_MANUAL_EXPOSURE_TIME_REAR", bracket_array[current_count][1].intValue());
                params.set("RIC_MANUAL_EXPOSURE_ISO_REAR", bracket_array[current_count][0].intValue());
                // for future possibilities we add this but it turns out to be discarded
                params.set("RIC_MANUAL_EXPOSURE_TIME_FRONT", bracket_array[current_count][1].intValue());
                params.set("RIC_MANUAL_EXPOSURE_ISO_FRONT", bracket_array[current_count][0].intValue());

                // always fic wb to 6500 to make sure pictures are taken in same way
                // exif info doesn't take this value. so you can only visually verify
                //params.set("RIC_WB_MODE",  "RicWbPrefixTemperature");
                //params.set("RIC_WB_TEMPERATURE",  "5100");


            }

            bcnt = bcnt - 1;
            if (bracket_array[current_count][4] == 1.0)
            {
                mCamera.setParameters(params);
                Intent intent = new Intent("com.theta360.plugin.ACTION_AUDIO_SHUTTER");
                sendBroadcast(intent);
                mCamera.takePicture(null, null, null, pictureListener);
            }
            else
            {
                // full white going on
                Log.i(TAG,"Full white picture copy.");
                pictureListener.onPictureTaken(saved_white_data,mCamera);

            }
        }

        else{
            //////////////////////////////////////////////////////////////////////////
            //                                                                      //
            //                          HDR MERGE                                   //
            //                                                                      //
            //////////////////////////////////////////////////////////////////////////

            Log.i(TAG,"Done with picture taking, let's start with the HDR merge.");

            //Log.d(TAG,"images is: "+Integer.toString(images_before_avg.size()) );
            Log.d(TAG,"times length is: " + Long.toString(times.total()));
            notificationLedBlink(LedTarget.LED3, LedColor.BLUE, 300);
            String opath ="";

            //Log.d(TAG,"starting align");
            //org.opencv.photo.AlignMTB align = org.opencv.photo.Photo.createAlignMTB();
            //align.process(images,images);
            if (number_of_noise_pics==1){
                images_filename_array = filename_array;
            }
            else {
                Log.i(TAG, "Merging average pics for denoise.");
                for (Integer i = 0; i < numberOfPictures; i++) {
                    average_pic = new Mat(rows, cols, CvType.CV_32FC3, new Scalar((float) (0.0), (float) (0.0), (float) (0.0)));

                    for (Integer j = 0; j < number_of_noise_pics; j++) {
                        notificationLedBlink(LedTarget.LED3, LedColor.RED, 300);
                        temp_pic = imread(filename_array.get(i * number_of_noise_pics + j));
                        temp_pic.convertTo(temp_pic, CvType.CV_32FC3);
                        Core.add(average_pic, temp_pic, average_pic);
                        temp_pic.release();
                        notificationLedBlink(LedTarget.LED3, LedColor.BLUE, 300);

                    }
                    org.opencv.core.Core.divide(average_pic, new Scalar(((float) number_of_noise_pics + 0.0),
                            ((float) number_of_noise_pics + 0.0),
                            ((float) number_of_noise_pics + 0.0)), average_pic);
                    Log.d(TAG, "Total average value " + Double.toString(average_pic.get(1, 1)[0]));

                    opath = filename_array.get(i * number_of_noise_pics + number_of_noise_pics - 1);
                    opath = opath.replace("c1", "avg");
                    Log.i(TAG, "Saving Averaged file as " + opath + ".");
                    notificationLedBlink(LedTarget.LED3, LedColor.RED, 300);
                    imwrite(opath, average_pic);
                    average_pic.release();
                    images_filename_array.add(opath);
                }
            }
            notificationLedBlink(LedTarget.LED3, LedColor.BLUE, 300);
            images = new ArrayList<Mat>(numberOfPictures);
            for (Integer i=0;i<numberOfPictures; i++)
            {
                String name = images_filename_array.get(i);
                Log.d(TAG,"Adding file "+ name);
                images.add(imread(name));
            }


            Log.i(TAG,"Starting calibration.");
            notificationLedBlink(LedTarget.LED3, LedColor.RED, 300);
            Mat responseDebevec = new Mat(256,1,CvType.CV_32FC3,new Scalar((float) (0.0), (float) (0.0), (float) (0.0)));
            //org.opencv.photo.CalibrateDebevec calibrateDebevec = org.opencv.photo.Photo.createCalibrateDebevec(70,100,false);
            //calibrateDebevec.process(images, responseDebevec, times);


            // The InputStream opens the resourceId and sends it to the buffer
            InputStream is = this.getResources().openRawResource(R.raw.master_crc_kasper);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String readLine = null;

            try {
                // While the BufferedReader readLine is not null
                Integer i =0;
                double[] data = new double[3];
                while ((readLine = br.readLine()) != null) {
                    //Log.d(TAG, readLine);
                    data[0] = Double.valueOf(readLine.split(" ")[0]);
                    data[1] = Double.valueOf(readLine.split(" ")[1]);
                    data[2] = Double.valueOf(readLine.split(" ")[2]);
                    responseDebevec.put(i,0,data);
                    i++;
                }

                // Close the InputStream and BufferedReader
                is.close();
                br.close();

            } catch (IOException e) {
                e.printStackTrace();
            }



            Log.i(TAG,"Calibration done, start saving curves.");
            notificationLedBlink(LedTarget.LED3, LedColor.BLUE, 300);

            try
            {
                // We save the Camera Curve to disk
                String filename = Environment.getExternalStorageDirectory().getPath()+ "/DCIM/100RICOH/" + session_name + "/CameraCurve.txt";
                BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
                for( Integer i=0; i<responseDebevec.rows(); i++)
                {
                    for( Integer j=0; j<responseDebevec.cols(); j++)
                    {
                        writer.write(Double.toString(responseDebevec.get(i,j)[0])+" "+
                                         Double.toString(responseDebevec.get(i,j)[1])+" "+
                                         Double.toString(responseDebevec.get(i,j)[2])+"\n");

                    }
                }
                writer.close();
                Log.i(TAG,"Calibration done saving times.");

                // We save the exposure times to disk
                filename = Environment.getExternalStorageDirectory().getPath()+ "/DCIM/100RICOH/" + session_name + "/Times.txt";
                writer = new BufferedWriter(new FileWriter(filename));
                for( Integer i=0; i<numberOfPictures; i++)
                {
                    writer.write(Double.toString(times.get(i,0)[0])+"\n");
                }
                writer.close();
            }
            catch(IOException e)
            {
                Log.i(TAG,"IO error");
            }
            notificationLedBlink(LedTarget.LED3, LedColor.RED, 300);

            Log.i(TAG,"Preping merge.");
            Mat hdrDebevec = new Mat();
            org.opencv.photo.MergeDebevec mergeDebevec = org.opencv.photo.Photo.createMergeDebevec();

            Log.i(TAG,"Starting merge.");
            mergeDebevec.process(images, hdrDebevec, times, responseDebevec);

            // Start Saving HDR Files.

            // We divide by the mean value of the whole picture to get the exposure values with a proper range.
            // Multiplied by 2 to get average value around 0.5 and 1.0, this has a better starting point.

            Scalar mean =  org.opencv.core.Core.mean(hdrDebevec);
            Log.d(TAG,"Mean: " + mean.toString());
            double new_mean = (mean.val[0]*2 + mean.val[1]*2 +mean.val[2]*2 )/3.0;
            Log.i(TAG,"Average Mean: " + Double.toString(new_mean));
            org.opencv.core.Core.divide(hdrDebevec,new Scalar(new_mean,new_mean,new_mean,0),hdrDebevec);


            Log.i(TAG,"Doing White balance.");
            notificationLedBlink(LedTarget.LED3, LedColor.BLUE, 300);
            // Do white balance thing, we take the auto_pic detect in that one all the white pixels.
            // Save those positions
            // then check those pixels in the HDR merge en compensate the average value to be white again.


            int low_value = 80;
            int high_value = 128;

            Mat mask = new Mat();
            Mat coord = new Mat();
            Mat mask_pic_w = new Mat(rows, cols, CvType.CV_8UC3, new Scalar(255, 255, 255));
            Mat mask_pic = new Mat(rows, cols, CvType.CV_8UC3, new Scalar(0, 0, 0));

            temp_pic = imread(auto_pic);

            Log.i(TAG,"Going through all white pixels.");
            for (int i = low_value; i < high_value; i++)
            {
                Core.inRange(temp_pic,new Scalar(i,i,i),new Scalar(i+3,i+3,i+3),mask);
                Core.bitwise_or(mask_pic_w,mask_pic,mask_pic,mask);
            }


            temp_pic.release();
            org.opencv.imgproc.Imgproc.cvtColor(mask_pic, temp_pic, org.opencv.imgproc.Imgproc.COLOR_RGB2GRAY);

            Core.findNonZero(temp_pic,coord);

            temp_pic.release();
            mask.release();
            mask_pic.release();
            mask_pic_w.release();


            Mat avg = new Mat(1, 1, CvType.CV_32FC3, new Scalar(0.0, 0.0, 0.0));

            Log.i(TAG,"Found "+Integer.toString(coord.rows())+" white pixels.");
            for (Integer j = 0; j < coord.rows(); j++)
            {
                org.opencv.core.Core.add(avg, new Scalar(   hdrDebevec.get((int)coord.get(j,0)[1], (int)coord.get(j,0)[0])[0],
                                                            hdrDebevec.get((int)coord.get(j,0)[1], (int)coord.get(j,0)[0])[1],
                                                            hdrDebevec.get((int)coord.get(j,0)[1], (int)coord.get(j,0)[0])[2],
                                                            0.0),avg);
            }
            org.opencv.core.Core.divide((double)coord.rows(),avg,avg);

            Log.d(TAG,"Average of white pixels is: " + String.valueOf(avg.get(0,0)[0])
                                + " " +String.valueOf(avg.get(0,0)[1])
                                +" "+String.valueOf(avg.get(0,0)[2]));



            double Y = (0.2126 * avg.get(0,0)[2] + 0.7152 * avg.get(0,0)[1] + 0.0722 * avg.get(0,0)[0]);
            Scalar multY = new Scalar(Y/avg.get(0,0)[0], Y/avg.get(0,0)[1], Y/avg.get(0,0)[2], 0.0);

            Log.d(TAG,"Brightness value is: " + String.valueOf(Y));
            Log.d(TAG,"Multiplying by: " + multY.toString());

            Mat hdrDebevecY = new Mat();
            org.opencv.core.Core.divide(hdrDebevec,multY,hdrDebevecY); // Why divide and not mult? works better don't understand.

            double B1 = hdrDebevec.get((int)coord.get(0,0)[1], (int)coord.get(0,0)[0])[0];
            double G1 = hdrDebevec.get((int)coord.get(0,0)[1], (int)coord.get(0,0)[0])[1];
            double R1 = hdrDebevec.get((int)coord.get(0,0)[1], (int)coord.get(0,0)[0])[2];
            Log.d(TAG,"Before: " + String.valueOf(B1) +" "+ String.valueOf(G1) +" "+ String.valueOf(R1));

            B1 = hdrDebevecY.get((int)coord.get(0,0)[1], (int)coord.get(0,0)[0])[0];
            G1 = hdrDebevecY.get((int)coord.get(0,0)[1], (int)coord.get(0,0)[0])[1];
            R1 = hdrDebevecY.get((int)coord.get(0,0)[1], (int)coord.get(0,0)[0])[2];
            Log.d(TAG,"After Y: " + String.valueOf(B1) +" "+ String.valueOf(G1) +" "+ String.valueOf(R1));

            B1 = hdrDebevec.get((int)coord.get(coord.rows()-1,0)[1], (int)coord.get(coord.rows()-1,0)[0])[0];
            G1 = hdrDebevec.get((int)coord.get(coord.rows()-1,0)[1], (int)coord.get(coord.rows()-1,0)[0])[1];
            R1 = hdrDebevec.get((int)coord.get(coord.rows()-1,0)[1], (int)coord.get(coord.rows()-1,0)[0])[2];
            Log.d(TAG,"Before end: " + String.valueOf(B1) +" "+ String.valueOf(G1) +" "+ String.valueOf(R1));

            B1 = hdrDebevecY.get((int)coord.get(coord.rows()-1,0)[1], (int)coord.get(coord.rows()-1,0)[0])[0];
            G1 = hdrDebevecY.get((int)coord.get(coord.rows()-1,0)[1], (int)coord.get(coord.rows()-1,0)[0])[1];
            R1 = hdrDebevecY.get((int)coord.get(coord.rows()-1,0)[1], (int)coord.get(coord.rows()-1,0)[0])[2];
            Log.d(TAG,"After Y end: " + String.valueOf(B1) +" "+ String.valueOf(G1) +" "+ String.valueOf(R1));

            /*
            opath = Environment.getExternalStorageDirectory().getPath()+ "/DCIM/100RICOH/" + session_name + "_nY.EXR";
            Log.i(TAG,"Saving EXR file as " + opath + ".");
            notificationLedBlink(LedTarget.LED3, LedColor.RED, 300);
            imwrite(opath, hdrDebevec,compressParams);
            */

            opath = Environment.getExternalStorageDirectory().getPath()+ "/DCIM/100RICOH/" + session_name + ".EXR";
            Log.i(TAG,"Saving EXR Y file as " + opath + ".");
            notificationLedBlink(LedTarget.LED3, LedColor.RED, 150);
            imwrite(opath, hdrDebevecY,compressParams);

            // We try the hack to copy the file with an jpg extension to make it accesable on windows
            Log.i(TAG,"Saving EXR as a jpg copy hack.");
            File source = new File(opath);

            // 5/16/2019 change by theta360.guide community
//            File target = new File(opath+"_removethis.JPG");
//            copyWithChannels(source, target, false);

            File from = new File(opath);
            File to = new File(opath+"_removethis.JPG");
            if(from.exists())
                from.renameTo(to);

            // end change by theta360.guide community



            Log.i(TAG,"Starting Tonemapping.");

            Mat ldrDrago = new Mat();
            org.opencv.photo.TonemapDrago tonemapDrago = org.opencv.photo.Photo.createTonemapDrago((float)1.0,(float)0.7);
            Log.i(TAG,"done creating tonemap.");

            tonemapDrago.process(hdrDebevecY, ldrDrago);
            //ldrMantiuk = 3 * ldrMantiuk;
            Log.i(TAG,"Multiplying tonemap.");

            org.opencv.core.Core.multiply(ldrDrago, new Scalar(3*255,3*255,3*255), ldrDrago);

            notificationLedBlink(LedTarget.LED3, LedColor.BLUE, 300);

            //StringBuilder sb = new StringBuilder(session_name);
            //sb.deleteCharAt(2);
            //String resultString = sb.toString();

            opath = Environment.getExternalStorageDirectory().getPath()+ "/DCIM/100RICOH/" + session_name + ".JPG";
            Log.i(TAG,"Saving tonemapped file as " + opath + ".");
            //org.opencv.core.Core.multiply(ldrMantiuk, new Scalar(255,255,255), ldrMantiuk);
            imwrite(opath, ldrDrago );

            //  need do some stuff with exif data to fix reading in app

            /*
            //Drawable drawable = getResources().getDrawable(android.R.drawable.ref);
            //Bitmap bitmap = BitmapFactory.decodeResource(getResources(),R.drawable.ref);
            String opath_exif = Environment.getExternalStorageDirectory().getPath()+ "/DCIM/100RICOH/exif_file.JPG";

            File file = new File(opath_exif);

            try {
                InputStream is_jpg = getResources().openRawResource(R.raw.ref);;
                OutputStream os = new FileOutputStream(file);
                byte[] data = new byte[is_jpg.available()];
                is_jpg.read(data);
                os.write(data);
                is_jpg.close();
                os.close();
            } catch (IOException e) {
                // Unable to create file, likely because external storage is
                // not currently mounted.
                Log.i("ExternalStorage", "Error writing " + file, e);
            }
            Log.i(TAG,"Exif copy ");
            try
            {
                ExifInterface tone_mapped_Exif = new ExifInterface(opath);
                ExifInterface ref_exif =  new ExifInterface(opath_exif);
                tone_mapped_Exif = ref_exif;
                tone_mapped_Exif.saveAttributes();
            }
            catch (Exception e)
            {
                Log.i(TAG,"Exif error.");
                e.printStackTrace();
                Log.i(TAG,"end exif error.");
            }
            */

            Log.i(TAG,"File saving done.");
            hdrDebevec.release();
            hdrDebevecY.release();

            coord.release();

            ldrDrago.release();
            responseDebevec.release();

            Log.i(TAG,"----- JOB DONE -----");
            notificationLedBlink(LedTarget.LED3, LedColor.MAGENTA, 2000);
            notificationLedHide(LedTarget.LED3);
            notificationLedShow(LedTarget.LED3);
            notificationLed3Show(LedColor.MAGENTA);

            Intent intent = new Intent("com.theta360.plugin.ACTION_AUDIO_SH_CLOSE");
            sendBroadcast(intent);
        }

    }
    private double find_closest_shutter(double shutter_in)
    {
        int i;
        for( i=0; i<shutter_table.length; i++){
            if (shutter_table[i][1] > shutter_in) {
                break;
            }
        }
        return shutter_table[i][0];
    }

    private Camera.PictureCallback pictureListener = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            //save image to storage
            Log.d(TAG,"onpicturetaken called ok");
            if (data != null) {

                try {
                    String tname = getNowDate();
                    String extra;
                    if ( m_is_auto_pic)
                    {
                        // get picture info, iso and shutter
                        Camera.Parameters params = mCamera.getParameters();
                        String flattened = params.flatten();
                        Log.d(TAG,flattened);
                        StringTokenizer tokenizer = new StringTokenizer(flattened, ";");
                        String text;
                        String cur_shutter = "";
                        String cur_iso  = "";
                        while (tokenizer.hasMoreElements())
                        {
                            text = tokenizer.nextToken();
                            if (text.contains("cur-exposure-time"))
                            {
                                cur_shutter = text.split("=")[1];
                                Log.d(TAG,"INFO after: "+text);
                            }
                            /*else if (text.contains("RIC_"))
                            {
                                Log.d("INFO" ,"after: "+text);
                            }*/
                            else if (text.contains("cur-iso"))
                            {
                                cur_iso = text.split("=")[1];
                                Log.d(TAG,"INFO after: "+text);
                            }
                        }

                        // Here we populate the bracket_array based on the base auto exposure picture.
                        extra = "auto_pic";

                        // cur_shutter is in mille seconds and a string
                        Float shutter = Float.parseFloat(cur_shutter)/1000;
                        Float iso_flt  =  Float.parseFloat(cur_iso);

                        Float new_shutter = shutter * iso_flt/100*2;
                        //find_closest_shutter(new_shutter);
                        Log.d(TAG,"New shutter number " + Double.toString(new_shutter));

                        Log.d(TAG,"Closest shutter number " + Double.toString(find_closest_shutter(new_shutter)));

                        // We adjust the stop jumps based on the current shutter number
                        // if base exposure time is low/short we are in light situation --> smaller jumps
                        // if base exposure time is a lager number = longer time we are in dark situation --> bigger jumps to reach overexposure and we are soon at
                        // < 1/1000 --> 1
                        // < 1/500  --> 1.5
                        // < 1/50   --> 2
                        // < 1/20   --> 2.5

                        // default is 2.5
                        if (new_shutter <= 0.02)   {stop_jumps = 2.0;}
                        if (new_shutter <= 0.002)  {stop_jumps = 1.5;}
                        if (new_shutter <= 0.001)  {stop_jumps = 1.0;}
                        if (new_shutter <= 0.0002) {stop_jumps = 0.5;}
                        Log.i(TAG,"Stop jumps are set to ----> "+Double.toString(stop_jumps) + ".");

                        // iso is always the lowest for now maybe alter we can implement a fast option with higher iso
                        // bracket_array =
                        // {{iso,shutter,bracketpos, shutter_length_real, go_ahead },{iso,shutter,bracketpos,shutter_length_real, go_ahead },{iso,shutter,bracketpos,shutter_length_real, go_ahead },....}
                        // {{50, 1/50, 0},{50, 1/25, +1},{50,1/100,-1},{50,1/13,+2},....}
                        // go_aherad is to turn of pictur takinfg when pict get full white or full black by default set 1, 0 means no pic
                        for( int i=0; i<numberOfPictures; i++)
                        {
                            boolean reached_18 = false;
                            bracket_array[i][0] = 1.0;
                            bracket_array[i][4] = 1.0;
                            // 0=0  1 = *2,+1  2 = /2, -1, 3 = *4=2^2,+2, 4=/4=2^2,-2 5 = *8=2^3,+3, 6 = /8=2^3
                            if ( (i & 1) == 0 )
                            {
                                //even...
                                bracket_array[i][1] = find_closest_shutter(new_shutter/( Math.pow(2,stop_jumps *  Math.ceil(i/2.0))));
                                bracket_array[i][2] = -1 * Math.ceil(i/2.0);
                                bracket_array[i][3] = shutter_table[bracket_array[i][1].intValue()][1];
                                times.put(i,0, shutter_table[bracket_array[i][1].intValue()][1]);
                            }
                            else
                             {
                                 //odd...
                                 Double corrected_shutter = new_shutter*(Math.pow(2,stop_jumps *Math.ceil(i/2.0)));
                                 int iso = 1;

                                 int j;
                                 for( j=1; j<shutter_table.length-1; j++){
                                     if (shutter_table[j][1] > corrected_shutter) {
                                         break;
                                     }
                                 }
                                 bracket_array[i][3] = shutter_table[j][1];
                                 times.put(i,0, shutter_table[j][1]);

                                 if ((corrected_shutter >= 1.0))
                                 {
                                     // If shutter value goes above 1 sec we increase iso unless we have reached highest iso already

                                     while (corrected_shutter >=1.0 && !( reached_18))
                                     {
                                         corrected_shutter = corrected_shutter/2.0;
                                         if (iso == 1) { iso =3; }
                                         else          { iso = iso + 3; }
                                         if (iso >=18)
                                         {
                                             iso=18;
                                             //if (reached_18) {corrected_shutter = corrected_shutter * 2.0;}
                                             reached_18 = true;

                                         }

                                     }
                                 }
                                 if ((reached_18) && (bracket_array[i-2][0] == 18))
                                 {
                                     // previous one was already at highest iso.
                                     bracket_array[i][0] = 18.0;
                                     bracket_array[i][1] = find_closest_shutter(corrected_shutter);

                                 }
                                 bracket_array[i][0] = Double.valueOf(iso);
                                 bracket_array[i][1] = find_closest_shutter(corrected_shutter);
                                 bracket_array[i][2] = Math.ceil(i/2.0);

                             }
                            Log.i(TAG,"Array: index "+Integer.toString(i) +
                                    " iso #: "+Integer.toString(bracket_array[i][0].intValue())+
                                    " shutter #: "+Integer.toString(bracket_array[i][1].intValue())+
                                    " bracketpos : "+Integer.toString(bracket_array[i][2].intValue())+
                                    " real shutter length : "+Double.toString(bracket_array[i][3]));
                        }
                        m_is_auto_pic = false;
                    }
                    else // not auto pic so we are in bracket loop
                    {
                        String nul ="";
                        if (current_count<10){nul ="0";}
                        if ( (current_count & 1) == 0 ) {
                            //even is min

                            extra = "i" + nul + Integer.toString(current_count) + "_m" + Integer.toString(Math.abs(bracket_array[current_count][2].intValue()));
                        }
                        else
                        {
                            //oneven is plus
                            extra = "i" + nul + Integer.toString(current_count) + "_p" + Integer.toString(bracket_array[current_count][2].intValue());
                        }

                        extra += "_c" + Integer.toString(noise_count);
                        if (noise_count == 1)
                        {
                            current_count++;
                            noise_count = number_of_noise_pics;
                        }
                        else
                        {
                            noise_count--;
                        }

                    }

                    //sort array from high to low
                    //Arrays.sort(bracket_array, (a, b) -> Double.compare(a[2], b[2]));

                    String opath = Environment.getExternalStorageDirectory().getPath()+ "/DCIM/100RICOH/" +  session_name + "/" + extra + ".jpg";
                    //String opath = Environment.getExternalStorageDirectory().getPath()+ "/DCIM/100RICOH/IMG_" + Integer.toString(current_count) + ".JPG";

                    FileOutputStream fos;
                    fos = new FileOutputStream(opath);
                    fos.write(data);




                    ExifInterface exif = new ExifInterface(opath);
/*
                    if (!extra.contains("auto_pic")) // setup opencv array for hdr merge
                            {
                                //Log.i(TAG,"adding to whole: "+opath);
                                images_before_avg.add(imread(opath));
                            }
*/

                    // firmware 3.00 doesn't support the tag shuuter_speed_value anymore
                    // But thiw whole piece was just extra info so let's throw it out

                    /*
                    String shutter_str = exif.getAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE);
                    Float shutter_flt = (Float.parseFloat(shutter_str.split("/")[0]) / Float.parseFloat(shutter_str.split("/")[1]));
                    String out ="";
                    if ( shutter_flt>0 )
                    {
                        out = "1/"+Double.toString(Math.floor(Math.pow(2,shutter_flt)));
                    }
                    else
                    {
                        out = Double.toString(1.0/(Math.pow(2,shutter_flt)));
                    }
                    */

                    //String shttr_str = exif.getAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE);
                    //Log.i(TAG,"shutter_float is" + shutter_flt);
                    Float shutter_speed_float = Float.parseFloat(exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME));
                    DecimalFormat df = new DecimalFormat("00.00000");
                    df.setMaximumFractionDigits(5);
                    String shutter_speed_string = df.format(shutter_speed_float);

                    //File fileold = new File(opath);
                    String opath_new = Environment.getExternalStorageDirectory().getPath()+ "/DCIM/100RICOH/" +
                            session_name + "/" + extra +
                            "_iso" +exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS) +
                            "_shutter" + shutter_speed_string +
                            "sec.jpg";
                    //File filenew = ;

                    if (!extra.contains("auto_pic")) // save filename for easy retrieve later on
                    {
                        filename_array.add(opath_new);
                    }
                    else
                    {
                        auto_pic = opath_new;
                    }
                    // check for full white pic and replace that with deafult white jpg to save time
                    Log.d(TAG,"_c" + Integer.toString(number_of_noise_pics)+"_");
                    if (opath_new.contains("_c" + Integer.toString(number_of_noise_pics)+"_"))
                    {
                        Log.i(TAG, "checking for full white");
                        temp_pic = new Mat();
                        temp_pic = imread(opath);
                        Scalar mean = org.opencv.core.Core.mean(temp_pic);
                        temp_pic.release();
                        double new_mean = (mean.val[0] + mean.val[1] + mean.val[2]) / 3.0;
                        Log.i(TAG, "Average Mean: " + Double.toString(new_mean));
                        if (new_mean == 255.0)
                        {
                                // We can skip these images and replace them with resource white jpg
                                // because they are full white
                                white_picture = opath_new;
                                saved_white_data = data;

                                for (int i=current_count; i < numberOfPictures;i=i+2)
                                {
                                    bracket_array[i][4] = 0.0;
                                    Log.i(TAG, "no pic on: " + Double.toString(i));
                                }
                        }
                    }



                    new File(opath).renameTo(new File(opath_new));
                    Log.i(TAG,"Saving file " + opath_new);
                    Log.i(TAG,"Shot with iso " + exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS) +" and a shutter of "+  shutter_speed_string + " sec.\n");
                    Log.d(TAG,"EXIF iso value: " + exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS));
                    //Log.d(TAG,"EXIF shutter value " + exif.getAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE) + " or " + out + " sec.");
                    Log.d(TAG,"EXIF shutter value/exposure value " + exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME) + " sec.");
                    //Log.d(TAG,"EXIF Color Temp: " + exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE));
                    //Log.d(TAG,"EXIF white point: " + exif.getAttribute(ExifInterface.TAG_WHITE_POINT));


                    fos.close();
                    registImage(tname, opath, mcontext, "image/jpeg");
                } catch (Exception e) {
                    Log.i(TAG,"Begin big error.");
                    e.printStackTrace();
                    Log.i(TAG,"End big error.");

                }

                nextShutter();
            }
        }
    };
    private static String getNowDate(){
        final DateFormat df = new SimpleDateFormat("HH_mm_ss");
        final Date date = new Date(System.currentTimeMillis());
        return df.format(date);
    }

    private static String getSessionName(){
        final DateFormat df = new SimpleDateFormat("MMddHHmm");
        final Date date = new Date(System.currentTimeMillis());
        return "R" + df.format(date) ;
    }

    private static void registImage(String fileName, String filePath, Context mcontext, String mimetype) {
        ContentValues values = new ContentValues();
        ContentResolver contentResolver = mcontext.getContentResolver();
        //"image/jpeg"
        values.put(MediaStore.Images.Media.MIME_TYPE, mimetype);
        values.put(MediaStore.Images.Media.TITLE, fileName);
        values.put("_data", filePath);
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }


}



