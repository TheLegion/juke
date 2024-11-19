import {DragDropModule} from '@angular/cdk/drag-drop';
import {ScrollingModule} from '@angular/cdk/scrolling';
import {NgModule} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSliderModule} from '@angular/material/slider';
import {MatTooltipModule} from '@angular/material/tooltip';
import {BrowserModule} from '@angular/platform-browser';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {RxStomp, RxStompConfig} from '@stomp/rx-stomp';
import {AppComponent} from './app.component';
import {DurationPipe} from './duration.pipe';
import {TrackControlComponent} from './track-control/track-control.component';
import {TrackComponent} from './track/track.component';
import {MAT_RIPPLE_GLOBAL_OPTIONS, RippleGlobalOptions} from '@angular/material/core';

@NgModule({
    declarations: [
        AppComponent,
        TrackComponent,
        TrackControlComponent,
        DurationPipe,
    ],
    imports: [
        BrowserModule,
        BrowserAnimationsModule,
        MatCardModule,
        MatSliderModule,
        MatButtonModule,
        MatIconModule,
        MatInputModule,
        MatProgressBarModule,
        DragDropModule,
        ScrollingModule,
        MatTooltipModule,
    ],
    providers: [
        {
            provide: RxStompConfig,
            useFactory: () => {
                const config = new RxStompConfig();
                config.brokerURL = `ws://${location.host}/api`;
                config.reconnectDelay = 500;

                return config;
            },
        },
        {
            provide: RxStomp,
            useFactory: (config: RxStompConfig) => {
                const rxStomp = new RxStomp();
                rxStomp.configure(config);
                rxStomp.activate();
                return rxStomp;
            },
            deps: [RxStompConfig],
        },
        {
            provide: MAT_RIPPLE_GLOBAL_OPTIONS,
            useValue: {disabled: true} satisfies RippleGlobalOptions
        }
    ],
    bootstrap: [AppComponent],
})
export class AppModule {
}
