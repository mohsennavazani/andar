/**
	Copyright (C) 2009,2010  Tobias Domhan

    This file is part of AndOpenGLCam.

    AndObjViewer is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    AndObjViewer is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with AndObjViewer.  If not, see <http://www.gnu.org/licenses/>.
 
 */
package edu.dhbw.andar;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;


import edu.dhbw.andar.exceptions.AndARRuntimeException;
import edu.dhbw.andar.interfaces.OpenGLRenderer;
import edu.dhbw.andar.util.IO;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.view.SurfaceHolder.Callback;

public abstract class AndARActivity extends Activity implements Callback, UncaughtExceptionHandler{
	private GLSurfaceView glSurfaceView;
	private Camera camera;
	private AndARRenderer renderer;
	private Resources res;
	private CameraPreviewHandler cameraHandler;
	private boolean mPausing = false;
	private ARToolkit artoolkit;
	private CameraStatus camStatus = new CameraStatus();
	private boolean surfaceCreated = false;

	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.currentThread().setUncaughtExceptionHandler(this);
        res = getResources();
        
        artoolkit = new ARToolkit(res, getFilesDir());
        setFullscreen();
        disableScreenTurnOff();
        //orientation is set via the manifest
        
        try {
			IO.transferFilesToPrivateFS(getFilesDir(),res);
		} catch (IOException e) {
			e.printStackTrace();
			throw new AndARRuntimeException(e.getMessage());
		}
        //glSurfaceView = new GLSurfaceView(this);
        glSurfaceView = new GLSurfaceView(this);
		renderer = new AndARRenderer(res, artoolkit, this);
		cameraHandler = new CameraPreviewHandler(glSurfaceView, renderer, res, artoolkit, camStatus);
        glSurfaceView.setRenderer(renderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        glSurfaceView.getHolder().addCallback(this);
        setContentView(glSurfaceView);
        //if(Config.DEBUG)
        //	Debug.startMethodTracing("AndAR");
    }
    
    
    /**
     * Set a renderer that draws non AR stuff. Optional, may be set to null or omited.
     * and setups lighting stuff.
     * @param customRenderer
     */
    public void setNonARRenderer(OpenGLRenderer customRenderer) {
		renderer.setNonARRenderer(customRenderer);
	}

    /**
     * Avoid that the screen get's turned off by the system.
     */
	public void disableScreenTurnOff() {
    	getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
    			WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    
	/**
	 * Set's the orientation to landscape, as this is needed by AndAR.
	 */
    public void setOrientation()  {
    	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
    
    /**
     * Maximize the application.
     */
    public void setFullscreen() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
   
    public void setNoTitle() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
    } 
    
    @Override
    protected void onPause() {
    	mPausing = true;
        this.glSurfaceView.onPause();
        super.onPause();
        finish();
        if(cameraHandler != null)
        	cameraHandler.stopThreads();
    }
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();    	
    	System.runFinalization();
    	//if(Config.DEBUG)
    	//	Debug.stopMethodTracing();
    }
    
    

    @Override
    protected void onResume() {
    	mPausing = false;
    	glSurfaceView.onResume();
        super.onResume();
    }
    
    /* (non-Javadoc)
     * @see android.app.Activity#onStop()
     */
    @Override
    protected void onStop() {
    	super.onStop();
    }
    
    private void openCamera()  {
    	if (camera == null) {
	    	//camera = Camera.open();
    		camera = CameraHolder.instance().open();
    		
	        Parameters params = camera.getParameters();
	        //reduce preview frame size for performance reasons
	        params.setPreviewSize(240,160);
	        try {
	        	camera.setParameters(params);
	        } catch(RuntimeException ex) {
	        	ex.printStackTrace();
	        }
	        
	        params = camera.getParameters();
	        //try to set the preview format
	        params.setPreviewFormat(PixelFormat.YCbCr_420_SP);
	        try {
	        	camera.setParameters(params);
	        } catch(RuntimeException ex) {
	        	ex.printStackTrace();
	        }	        
	        if(!Config.USE_ONE_SHOT_PREVIEW) {
	        	camera.setPreviewCallback(cameraHandler);	 
	        } 
			try {
				cameraHandler.init(camera);
			} catch (Exception e) {
				e.printStackTrace();
			}			
    	}
    }
    
    private void closeCamera() {
        if (camera != null) {
        	CameraHolder.instance().keep();
        	CameraHolder.instance().release();
        	camera = null;
        	camStatus.previewing = false;
        }
    }
    
    /**
     * Open the camera and start detecting markers.
     */
    public void startPreview() {
    	if(!surfaceCreated) return;
    	if(mPausing) return;
    	if (camStatus.previewing) stopPreview();
    	openCamera();
		camera.startPreview();
		camStatus.previewing = true;
    }
    
    /**
     * Close the camera and stop detecting markers.
     */
    public void stopPreview() {
    	if (camera != null && camStatus.previewing ) {
    		camStatus.previewing = false;
            camera.stopPreview();
         }
    	
    }

	/* The GLSurfaceView changed
	 * @see android.view.SurfaceHolder.Callback#surfaceChanged(android.view.SurfaceHolder, int, int, int)
	 */
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		System.out.println("");
	}

	/* The GLSurfaceView was created
	 * The camera will be opened and the preview started 
	 * @see android.view.SurfaceHolder.Callback#surfaceCreated(android.view.SurfaceHolder)
	 */
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		surfaceCreated = true;
		if (Integer.parseInt(Build.VERSION.SDK) < 4) {
        	//for android 1.5 compatibilty reasons:
			 try {
				 if(holder != null)
					 camera.setPreviewDisplay(holder);
			 } catch (IOException e1) {
			        e1.printStackTrace();
			 }
        }
	}

	/* GLSurfaceView was destroyed
	 * The camera will be closed and the preview stopped.
	 * @see android.view.SurfaceHolder.Callback#surfaceDestroyed(android.view.SurfaceHolder)
	 */
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
        stopPreview();
        closeCamera();
	}
	
	/**
	 * @return  a the instance of the ARToolkit.
	 */
	public ARToolkit getArtoolkit() {
		return artoolkit;
	}	
	
	/**
	 * 
	 * @return the OpenGL surface.
	 */
	public SurfaceView getSurfaceView() {
		return glSurfaceView;
	}
}