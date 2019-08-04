import {BrowserModule} from '@angular/platform-browser';
import {NgModule} from '@angular/core';

import {AppComponent} from './app.component';
import {InjectableRxStompConfig, RxStompService, rxStompServiceFactory} from '@stomp/ng2-stompjs';
import {TrackComponent} from './track/track.component';
import {TrackControlComponent} from './track-control/track-control.component';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {MatButtonModule, MatCardModule, MatIconModule, MatInputModule, MatSliderModule} from '@angular/material';
import {DurationPipe} from './duration.pipe';
import {DragDropModule} from '@angular/cdk/drag-drop';
import {ScrollingModule} from '@angular/cdk/scrolling';

@NgModule({
    declarations: [
        AppComponent,
        TrackComponent,
        TrackControlComponent,
        DurationPipe
    ],
    imports: [
        BrowserModule,
        NoopAnimationsModule,
        MatCardModule,
        MatSliderModule,
        MatButtonModule,
        MatIconModule,
        MatInputModule,
        DragDropModule,
        ScrollingModule
    ],
    providers: [
        {
            provide: InjectableRxStompConfig,
            useFactory: () => ({
                brokerURL: `ws://${location.host}/api`,
                reconnectDelay: 500,
                debug: () => void 0
            } as InjectableRxStompConfig)
        },
        {
            provide: RxStompService,
            useFactory: rxStompServiceFactory,
            deps: [InjectableRxStompConfig]
        }
    ],
    bootstrap: [AppComponent]
})
export class AppModule {
}
