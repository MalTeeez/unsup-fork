/*
 * This file is part of unsup.
 * Copyright © 2025 Exa Skye
 * https://git.sleeping.town/exa/unsup
 *
 * unsup is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * unsup is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with unsup; if not, see <https://www.gnu.org/licenses/>.
 */

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
	
	@SuppressWarnings("unchecked")
	private <T> T typed(int i) { return (T)typed[i]; }
	
	public                SDL_CommonEvent common         () { return typed( 0); }
	public               SDL_DisplayEvent display        () { return typed( 1); }
	public                SDL_WindowEvent window         () { return typed( 2); }
	public        SDL_KeyboardDeviceEvent kdevice        () { return typed( 3); }
	public              SDL_KeyboardEvent key            () { return typed( 4); }
	public           SDL_TextEditingEvent edit           () { return typed( 5); }
	public SDL_TextEditingCandidatesEvent edit_candidates() { return typed( 6); }
	public             SDL_TextInputEvent text           () { return typed( 7); }
	public           SDL_MouseDeviceEvent mdevice        () { return typed( 8); }
	public           SDL_MouseMotionEvent motion         () { return typed( 9); }
	public           SDL_MouseButtonEvent button         () { return typed(10); }
	public            SDL_MouseWheelEvent wheel          () { return typed(11); }
	public             SDL_JoyDeviceEvent jdevice        () { return typed(12); }
	public               SDL_JoyAxisEvent jaxis          () { return typed(13); }
	public               SDL_JoyBallEvent jball          () { return typed(14); }
	public                SDL_JoyHatEvent jhat           () { return typed(15); }
	public             SDL_JoyButtonEvent jbutton        () { return typed(16); }
	public            SDL_JoyBatteryEvent jbattery       () { return typed(17); }
	public         SDL_GamepadDeviceEvent gdevice        () { return typed(18); }
	public           SDL_GamepadAxisEvent gaxis          () { return typed(19); }
	public         SDL_GamepadButtonEvent gbutton        () { return typed(20); }
	public       SDL_GamepadTouchpadEvent gtouchpad      () { return typed(21); }
	public         SDL_GamepadSensorEvent gsensor        () { return typed(22); }
	public           SDL_AudioDeviceEvent adevice        () { return typed(23); }
	public          SDL_CameraDeviceEvent cdevice        () { return typed(24); }
	public                SDL_SensorEvent sensor         () { return typed(25); }
	public                  SDL_QuitEvent quit           () { return typed(26); }
	public                  SDL_UserEvent user           () { return typed(27); }
	public           SDL_TouchFingerEvent tfinger        () { return typed(28); }
	public          SDL_PenProximityEvent pproximity     () { return typed(29); }
	public              SDL_PenTouchEvent ptouch         () { return typed(30); }
	public             SDL_PenMotionEvent pmotion        () { return typed(31); }
	public             SDL_PenButtonEvent pbutton        () { return typed(32); }
	public               SDL_PenAxisEvent paxis          () { return typed(33); }
	public                SDL_RenderEvent render         () { return typed(34); }
	public                  SDL_DropEvent drop           () { return typed(35); }
	public             SDL_ClipboardEvent clipboard      () { return typed(36); }
	
}
