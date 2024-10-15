import { DragDropModule } from '@angular/cdk/drag-drop';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { NgModule } from '@angular/core';
import { MatLegacyButtonModule as MatButtonModule } from '@angular/material/legacy-button';
import { MatLegacyCardModule as MatCardModule } from '@angular/material/legacy-card';
import { MatIconModule } from '@angular/material/icon';
import { MatLegacyInputModule as MatInputModule } from '@angular/material/legacy-input';
import { MatLegacySliderModule as MatSliderModule } from '@angular/material/legacy-slider';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { RxStomp, RxStompConfig } from '@stomp/rx-stomp';
import { AppComponent } from './app.component';
import { DurationPipe } from './duration.pipe';
import { TrackControlComponent } from './track-control/track-control.component';
import { TrackComponent } from './track/track.component';

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
            ],
            bootstrap: [AppComponent],
          })
export class AppModule {
}
