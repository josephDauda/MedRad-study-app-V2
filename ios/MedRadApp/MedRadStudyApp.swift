
import SwiftUI

@main
struct MedRadStudyApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Image(systemName: "waveform.path.ecg")
                    .font(.system(size: 55))
                    .foregroundStyle(.blue)

                Text("MedRad Study")
                    .font(.largeTitle)
                    .fontWeight(.bold)

                Text("Your Radiography Learning Companion")
                    .font(.headline)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)

                Divider()

                Text("Your iOS application is being built.")
                    .font(.body)
                    .multilineTextAlignment(.center)
            }
            .padding(25)
            .navigationTitle("MedRad Study")
        }
    }
}
