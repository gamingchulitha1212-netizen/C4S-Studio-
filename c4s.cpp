#include <iostream>
#include <string>
using namespace std;

int main() {
    string name;
    int packageChoice;
    
    cout << "===== C4S Studio =====" << endl;
    cout << "1. Basic   - Rs. 4,000" << endl;
    cout << "2. Standard - Rs. 5,000" << endl;
    cout << "3. Premium  - Rs. 10,000" << endl;
    cout << endl;
    
    cout << "Enter your name: ";
    cin >> name;
    
    cout << "Select package (1/2/3): ";
    cin >> packageChoice;
    
    cout << endl;
    cout << "Hello " << name << "!" << endl;
    
    switch(packageChoice) {
        case 1:
            cout << "You selected Basic package - Rs. 4,000" << endl;
            break;
        case 2:
            cout << "You selected Standard package - Rs. 5,000" << endl;
            break;
        case 3:
            cout << "You selected Premium package - Rs. 10,000" << endl;
            break;
        default:
            cout << "Invalid choice!" << endl;
    }
    
    return 0;
}