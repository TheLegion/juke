import {DragDropModule} from '@angular/cdk/drag-drop';
import {ScrollingModule} from '@angular/cdk/scrolling';
import {NgModule} from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {InjectableRxStompConfig, RxStompService, rxStompServiceFactory} from '@stomp/ng2-stompjs';

import {AppComponent} from './app.component';
import {DurationPipe} from './duration.pipe';
import {TrackControlComponent} from './track-control/track-control.component';
import {TrackComponent} from './track/track.component';
import {MatInputModule} from '@angular/material/input';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {MatSliderModule} from '@angular/material/slider';
import {MatCardModule} from '@angular/material/card';

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
              DragDropModule,
              ScrollingModule,
            ],
            providers: [
              {
                provide: InjectableRxStompConfig,
                useFactory: () => ({
                  brokerURL: `ws://${location.host}/api`,
                  reconnectDelay: 500,
                  debug: () => void 0,
                } as InjectableRxStompConfig),
              },
              {
                provide: RxStompService,
                useFactory: rxStompServiceFactory,
                deps: [InjectableRxStompConfig],
              },
            ],
            bootstrap: [AppComponent],
          })
export class AppModule {
}
