package com.unascribed.sup.puppet.opengl.util;

import org.lwjgl.sdl.*;

public class CachedSDLEvent {

	private final SDL_Event delegate;
	
	private final Object[] typed;

	public CachedSDLEvent(SDL_Event delegate) {
		this.delegate = delegate;
		
		this.typed = new Object[] {
			delegate.common(),
			delegate.display(),
			delegate.window(),
			delegate.kdevice(),
			delegate.key(),
			delegate.edit(),
			delegate.edit_candidates(),
			delegate.text(),
			delegate.mdevice(),
			delegate.motion(),
			delegate.button(),
			delegate.wheel(),
			delegate.jdevice(),
			delegate.jaxis(),
			delegate.jball(),
			delegate.jhat(),
			delegate.jbutton(),
			delegate.jbattery(),
			delegate.gdevice(),
			delegate.gaxis(),
			delegate.gbutton(),
			delegate.gtouchpad(),
			delegate.gsensor(),
			delegate.adevice(),
			delegate.cdevice(),
			delegate.sensor(),
			delegate.quit(),
			delegate.user(),
			delegate.tfinger(),
			delegate.pproximity(),
			delegate.ptouch(),
			delegate.pmotion(),
			delegate.pbutton(),
			delegate.paxis(),
			delegate.render(),
			delegate.drop(),
			delegate.clipboard(),
		};
	}
	
	public int type() { return delegate.type(); }
	
	public SDL_CommonEvent common() { return (SDL_CommonEvent)typed[0]; }
	public SDL_DisplayEvent display() { return (SDL_DisplayEvent)typed[1]; }
	public SDL_WindowEvent window() { return (SDL_WindowEvent)typed[2]; }
	public SDL_KeyboardDeviceEvent kdevice() { return (SDL_KeyboardDeviceEvent)typed[3]; }
	public SDL_KeyboardEvent key() { return (SDL_KeyboardEvent)typed[4]; }
	public SDL_TextEditingEvent edit() { return (SDL_TextEditingEvent)typed[5]; }
	public SDL_TextEditingCandidatesEvent edit_candidates() { return (SDL_TextEditingCandidatesEvent)typed[6]; }
	public SDL_TextInputEvent text() { return (SDL_TextInputEvent)typed[7]; }
	public SDL_MouseDeviceEvent mdevice() { return (SDL_MouseDeviceEvent)typed[8]; }
	public SDL_MouseMotionEvent motion() { return (SDL_MouseMotionEvent)typed[9]; }
	public SDL_MouseButtonEvent button() { return (SDL_MouseButtonEvent)typed[10]; }
	public SDL_MouseWheelEvent wheel() { return (SDL_MouseWheelEvent)typed[11]; }
	public SDL_JoyDeviceEvent jdevice() { return (SDL_JoyDeviceEvent)typed[12]; }
	public SDL_JoyAxisEvent jaxis() { return (SDL_JoyAxisEvent)typed[13]; }
	public SDL_JoyBallEvent jball() { return (SDL_JoyBallEvent)typed[14]; }
	public SDL_JoyHatEvent jhat() { return (SDL_JoyHatEvent)typed[15]; }
	public SDL_JoyButtonEvent jbutton() { return (SDL_JoyButtonEvent)typed[16]; }
	public SDL_JoyBatteryEvent jbattery() { return (SDL_JoyBatteryEvent)typed[17]; }
	public SDL_GamepadDeviceEvent gdevice() { return (SDL_GamepadDeviceEvent)typed[18]; }
	public SDL_GamepadAxisEvent gaxis() { return (SDL_GamepadAxisEvent)typed[19]; }
	public SDL_GamepadButtonEvent gbutton() { return (SDL_GamepadButtonEvent)typed[20]; }
	public SDL_GamepadTouchpadEvent gtouchpad() { return (SDL_GamepadTouchpadEvent)typed[21]; }
	public SDL_GamepadSensorEvent gsensor() { return (SDL_GamepadSensorEvent)typed[22]; }
	public SDL_AudioDeviceEvent adevice() { return (SDL_AudioDeviceEvent)typed[23]; }
	public SDL_CameraDeviceEvent cdevice() { return (SDL_CameraDeviceEvent)typed[24]; }
	public SDL_SensorEvent sensor() { return (SDL_SensorEvent)typed[25]; }
	public SDL_QuitEvent quit() { return (SDL_QuitEvent)typed[26]; }
	public SDL_UserEvent user() { return (SDL_UserEvent)typed[27]; }
	public SDL_TouchFingerEvent tfinger() { return (SDL_TouchFingerEvent)typed[28]; }
	public SDL_PenProximityEvent pproximity() { return (SDL_PenProximityEvent)typed[29]; }
	public SDL_PenTouchEvent ptouch() { return (SDL_PenTouchEvent)typed[30]; }
	public SDL_PenMotionEvent pmotion() { return (SDL_PenMotionEvent)typed[31]; }
	public SDL_PenButtonEvent pbutton() { return (SDL_PenButtonEvent)typed[32]; }
	public SDL_PenAxisEvent paxis() { return (SDL_PenAxisEvent)typed[33]; }
	public SDL_RenderEvent render() { return (SDL_RenderEvent)typed[34]; }
	public SDL_DropEvent drop() { return (SDL_DropEvent)typed[35]; }
	public SDL_ClipboardEvent clipboard() { return (SDL_ClipboardEvent)typed[36]; }
	
}
