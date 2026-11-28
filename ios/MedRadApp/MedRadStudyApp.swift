import SwiftUI

@main
struct MedRadStudyApp: App {
    var body: some Scene {
        WindowGroup {
            AppRootView()
                .preferredColorScheme(nil)
        }
    }
}

enum MedRadPalette {
    static let navy = Color(red: 0.07, green: 0.15, blue: 0.27)
    static let teal = Color(red: 0.08, green: 0.57, blue: 0.58)
    static let amber = Color(red: 0.96, green: 0.68, blue: 0.24)
    static let canvas = Color(uiColor: .systemGroupedBackground)
}

enum AppTab: String, CaseIterable {
    case home = "Home"
    case courses = "Courses"
    case feeds = "Feeds"
    case scholars = "Scholars"
    case tutors = "Tutors"

    var icon: String {
        switch self {
        case .home: "house.fill"
        case .courses: "book.closed.fill"
        case .feeds: "bubble.left.and.bubble.right.fill"
        case .scholars: "person.2.fill"
        case .tutors: "graduationcap.fill"
        }
    }
}

struct AppRootView: View {
    @State private var selectedTab: AppTab = .home

    var body: some View {
        TabView(selection: $selectedTab) {
            DashboardView(onSelect: { selectedTab = $0 })
                .tabItem { Label(AppTab.home.rawValue, systemImage: AppTab.home.icon) }
                .tag(AppTab.home)
            FeatureListView(title: "Courses", subtitle: "Your radiography learning library", icon: "book.closed.fill", accent: MedRadPalette.teal)
                .tabItem { Label(AppTab.courses.rawValue, systemImage: AppTab.courses.icon) }
                .tag(AppTab.courses)
            FeatureListView(title: "Medical Feeds", subtitle: "Community updates and learning resources", icon: "bubble.left.and.bubble.right.fill", accent: MedRadPalette.amber)
                .tabItem { Label(AppTab.feeds.rawValue, systemImage: AppTab.feeds.icon) }
                .tag(AppTab.feeds)
            FeatureListView(title: "Discover Scholars", subtitle: "Connect with fellow students", icon: "person.2.fill", accent: MedRadPalette.teal)
                .tabItem { Label(AppTab.scholars.rawValue, systemImage: AppTab.scholars.icon) }
                .tag(AppTab.scholars)
            FeatureListView(title: "Tutor Directory", subtitle: "Find learning support", icon: "graduationcap.fill", accent: MedRadPalette.amber)
                .tabItem { Label(AppTab.tutors.rawValue, systemImage: AppTab.tutors.icon) }
                .tag(AppTab.tutors)
        }
        .tint(MedRadPalette.teal)
    }
}

struct DashboardView: View {
    var onSelect: (AppTab) -> Void
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    welcomeCard
                    HStack(spacing: 12) {
                        MetricCard(title: "Study streak", value: "—", symbol: "flame.fill", tint: MedRadPalette.amber)
                        MetricCard(title: "Progress", value: "—", symbol: "chart.bar.fill", tint: MedRadPalette.teal)
                    }
                    sectionHeader("Continue learning", action: { onSelect(.courses) })
                    FeatureTile(icon: "book.closed.fill", title: "Courses & materials", subtitle: "Explore your radiography modules", tint: MedRadPalette.teal) { onSelect(.courses) }
                    sectionHeader("Community", action: { onSelect(.feeds) })
                    FeatureTile(icon: "bubble.left.and.bubble.right.fill", title: "Medical Feeds", subtitle: "Updates, discussions and resources", tint: MedRadPalette.amber) { onSelect(.feeds) }
                    FeatureTile(icon: "person.2.fill", title: "Discover Scholars", subtitle: "Meet students in the community", tint: MedRadPalette.teal) { onSelect(.scholars) }
                    FeatureTile(icon: "graduationcap.fill", title: "Tutor Directory", subtitle: "Explore tutor profiles", tint: MedRadPalette.navy) { onSelect(.tutors) }
                }
                .padding()
            }
            .background(MedRadPalette.canvas)
            .navigationTitle("MedRad Study")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink(destination: ProfileView()) {
                        Image(systemName: "person.crop.circle").font(.title2)
                    }
                }
            }
        }
    }

    private var welcomeCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: "waveform.path.ecg").font(.title).foregroundStyle(.white)
            Text("Your radiography journey").font(.title2.bold()).foregroundStyle(.white)
            Text("Learn, practise and connect with your community.").foregroundStyle(.white.opacity(0.88))
            HStack { Spacer(); Image(systemName: "sparkles").font(.largeTitle).foregroundStyle(.white.opacity(0.25)) }
        }
        .padding(20)
        .background(LinearGradient(colors: [MedRadPalette.navy, MedRadPalette.teal], startPoint: .topLeading, endPoint: .bottomTrailing))
        .clipShape(RoundedRectangle(cornerRadius: 24))
    }
    private func sectionHeader(_ title: String, action: @escaping () -> Void) -> some View {
        HStack { Text(title).font(.headline); Spacer(); Button("See all", action: action).font(.subheadline).foregroundStyle(MedRadPalette.teal) }
    }
}

struct MetricCard: View {
    let title: String; let value: String; let symbol: String; let tint: Color
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: symbol).foregroundStyle(tint)
            Text(value).font(.title2.bold())
            Text(title).font(.caption).foregroundStyle(.secondary)
        }.frame(maxWidth: .infinity, alignment: .leading).padding().background(.background).clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

struct FeatureTile: View {
    let icon: String; let title: String; let subtitle: String; let tint: Color; let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon).font(.title2).foregroundStyle(tint).frame(width: 48, height: 48).background(tint.opacity(0.12)).clipShape(RoundedRectangle(cornerRadius: 14))
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.headline).foregroundStyle(.primary)
                    Text(subtitle).font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(.tertiary)
            }.padding().background(.background).clipShape(RoundedRectangle(cornerRadius: 18))
        }.buttonStyle(.plain)
    }
}

struct FeatureListView: View {
    let title: String; let subtitle: String; let icon: String; let accent: Color
    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Image(systemName: icon).font(.system(size: 48)).foregroundStyle(accent)
                Text(title).font(.title2.bold())
                Text(subtitle).multilineTextAlignment(.center).foregroundStyle(.secondary)
                Text("This section is being connected to the shared MedRad Study services.").font(.footnote).multilineTextAlignment(.center).foregroundStyle(.secondary).padding(.top, 8)
            }.padding(30).frame(maxWidth: .infinity, maxHeight: .infinity).background(MedRadPalette.canvas)
                .navigationTitle(title).navigationBarTitleDisplayMode(.inline)
        }
    }
}

struct ProfileView: View {
    var body: some View {
        List {
            Section("Account") {
                Label("Sign in / Create account", systemImage: "person.crop.circle")
                Label("Settings", systemImage: "gearshape")
            }
            Section {
                Text("iOS feature parity is under development. Your existing account and backend are not changed by this starter UI.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }.navigationTitle("Profile")
    }
}
